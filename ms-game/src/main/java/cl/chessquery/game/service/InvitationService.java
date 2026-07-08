package cl.chessquery.game.service;

import cl.chessquery.game.dto.LiveGameDtos.InvitationResponse;
import cl.chessquery.game.entity.GameInvitation;
import cl.chessquery.game.entity.GameInvitation.InvitationStatus;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.repository.GameInvitationRepository;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Máquina de estados de las invitaciones a partida en vivo (EP-P1 / P1-03).
 *
 * PENDING → ACCEPTED / DECLINED (por el invitado) · PENDING → EXPIRED (por TTL).
 * Cada transición publica su evento {@code invite.*}. La expiración ocurre por
 * el {@link InvitationExpiryScheduler} y, de forma perezosa, en {@link #get} para
 * que el polling del countdown vea el estado correcto sin esperar al job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    static final long DEFAULT_TTL_SECONDS = 60;
    static final long MIN_TTL_SECONDS = 10;
    static final long MAX_TTL_SECONDS = 600;

    private final GameInvitationRepository invRepo;
    private final LiveGameSessionRepository sessionRepo;
    private final EventPublisherService events;

    /** Crea una invitación PENDING con TTL y publica invite.created. */
    @Transactional
    public GameInvitation create(Long sessionId, Long fromPlayerId, Long toPlayerId,
                                 String toEmail, String color, Long ttlSeconds) {
        LiveGameSession s = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(404, "SESSION_NOT_FOUND",
                        "La sesión no existe"));
        if (s.getStatus() != SessionStatus.WAITING) {
            throw new ApiException(409, "SESSION_NOT_WAITING",
                    "Solo se puede invitar a una sesión en espera");
        }

        long ttl = clampTtl(ttlSeconds);
        Instant now = Instant.now();
        String inviteeColor = (color == null || color.isBlank()) ? "b" : color;

        GameInvitation inv = GameInvitation.builder()
                .sessionId(sessionId)
                .fromPlayerId(fromPlayerId)
                .toPlayerId(toPlayerId)
                .toEmail(toEmail)
                .inviteeColor(inviteeColor)
                .timeControlInitialMs(s.getTimeControlInitialMs())
                .timeControlIncrementMs(s.getTimeControlIncrementMs())
                .status(InvitationStatus.PENDING)
                .expiresAt(now.plusSeconds(ttl))
                .createdAt(now)
                .build();
        inv = invRepo.save(inv);

        events.publishInviteCreated(inv.getId(), sessionId, fromPlayerId, toPlayerId,
                s.getTimeControlInitialMs(), s.getTimeControlIncrementMs(),
                inviteeColor, inv.getExpiresAt(), ttl);
        log.info("invite.created id={} session={} to={} ttl={}s", inv.getId(), sessionId, toEmail, ttl);
        return inv;
    }

    /** El invitado acepta. Falla si ya no está PENDING o si venció (410). */
    @Transactional
    public GameInvitation accept(Long inviteId, Long playerId) {
        GameInvitation inv = requirePending(inviteId);
        requireInvitee(inv, playerId);
        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setRespondedAt(Instant.now());
        invRepo.save(inv);
        events.publishInviteAccepted(inv.getId(), inv.getSessionId(), inv.getRespondedAt());
        log.info("invite.accepted id={} by player={}", inviteId, playerId);
        return inv;
    }

    /** El invitado rechaza con motivo opcional. */
    @Transactional
    public GameInvitation decline(Long inviteId, Long playerId, String reason) {
        GameInvitation inv = requirePending(inviteId);
        requireInvitee(inv, playerId);
        inv.setStatus(InvitationStatus.DECLINED);
        inv.setDeclineReason(reason);
        inv.setRespondedAt(Instant.now());
        invRepo.save(inv);
        events.publishInviteDeclined(inv.getId(), reason);
        log.info("invite.declined id={} by player={}", inviteId, playerId);
        return inv;
    }

    /** Lectura para el countdown; expira de forma perezosa si el TTL ya venció. */
    @Transactional
    public GameInvitation get(Long inviteId) {
        GameInvitation inv = invRepo.findById(inviteId)
                .orElseThrow(() -> new ApiException(404, "INVITE_NOT_FOUND",
                        "La invitación no existe"));
        if (inv.getStatus() == InvitationStatus.PENDING && isExpired(inv)) {
            expire(inv);
        }
        return inv;
    }

    /** Marca EXPIRED las PENDING vencidas. Devuelve cuántas expiró. */
    @Transactional
    public int expireDue() {
        List<GameInvitation> due =
                invRepo.findByStatusAndExpiresAtBefore(InvitationStatus.PENDING, Instant.now());
        due.forEach(this::expire);
        if (!due.isEmpty()) {
            log.info("Expiradas {} invitaciones vencidas", due.size());
        }
        return due.size();
    }

    public static InvitationResponse toResponse(GameInvitation inv) {
        return new InvitationResponse(
                inv.getId(), inv.getSessionId(), inv.getFromPlayerId(), inv.getToPlayerId(),
                inv.getToEmail(), inv.getInviteeColor(),
                inv.getTimeControlInitialMs(), inv.getTimeControlIncrementMs(),
                inv.getStatus().name(), inv.getDeclineReason(),
                inv.getExpiresAt(), inv.getRespondedAt(), inv.getCreatedAt(),
                inv.getToPlayerId() != null);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private GameInvitation requirePending(Long inviteId) {
        GameInvitation inv = invRepo.findById(inviteId)
                .orElseThrow(() -> new ApiException(404, "INVITE_NOT_FOUND",
                        "La invitación no existe"));
        if (inv.getStatus() != InvitationStatus.PENDING) {
            throw new ApiException(409, "INVITE_NOT_PENDING",
                    "La invitación ya fue respondida o expiró");
        }
        if (isExpired(inv)) {
            expire(inv);
            throw new ApiException(410, "INVITE_EXPIRED", "La invitación expiró");
        }
        return inv;
    }

    private void requireInvitee(GameInvitation inv, Long playerId) {
        // Un invitado con cuenta solo puede responder su propia invitación.
        if (inv.getToPlayerId() != null && !inv.getToPlayerId().equals(playerId)) {
            throw new ApiException(403, "NOT_INVITEE",
                    "Esta invitación no es para este jugador");
        }
    }

    private void expire(GameInvitation inv) {
        inv.setStatus(InvitationStatus.EXPIRED);
        invRepo.save(inv);
        events.publishInviteExpired(inv.getId(), inv.getSessionId(), Instant.now());
    }

    private boolean isExpired(GameInvitation inv) {
        return inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(Instant.now());
    }

    private long clampTtl(Long ttlSeconds) {
        if (ttlSeconds == null) return DEFAULT_TTL_SECONDS;
        return Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, ttlSeconds));
    }
}
