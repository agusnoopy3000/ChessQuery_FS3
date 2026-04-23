package cl.chessquery.notifications.service;

import cl.chessquery.notifications.entity.Channel;
import cl.chessquery.notifications.entity.NotifStatus;
import cl.chessquery.notifications.entity.NotificationLog;
import cl.chessquery.notifications.repository.NotificationLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final MockEmailService           mockEmailService;
    private final NotificationLogRepository  notificationLogRepo;
    private final ObjectMapper               objectMapper;

    /**
     * Email de bienvenida cuando un usuario se registra.
     * Payload: {userId, email, firstName, lastName, role}
     */
    @Transactional
    public void notifyWelcome(Map<String, Object> payload) {
        Long   recipientId = toLong(payload.get("userId"));
        String email       = (String) payload.get("email");
        String firstName   = (String) payload.get("firstName");

        String subject = "¡Bienvenido a ChessQuery!";
        String body    = String.format(
                "Hola %s, tu cuenta ha sido creada exitosamente. Email: %s",
                firstName, email);

        mockEmailService.sendEmail(recipientId, email, subject, body);
        saveLog(recipientId, Channel.EMAIL, "user.registered", subject, payload, NotifStatus.SENT);
    }

    /**
     * Confirmación de inscripción en torneo.
     * Payload: {playerId, tournamentId, seedRating, registeredAt}
     */
    @Transactional
    public void notifyRegistration(Map<String, Object> payload) {
        Long recipientId  = toLong(payload.get("playerId"));
        Object tournamentId = payload.get("tournamentId");
        Object seedRating   = payload.get("seedRating");

        String subject = "Inscripción confirmada en torneo";
        String body    = String.format(
                "Te has inscrito en el torneo %s con rating de seed %s.",
                tournamentId, seedRating);

        // En un sistema real se obtendría el email del jugador desde MS-Users.
        // Aquí usamos el recipientId como identificador del destinatario.
        mockEmailService.sendEmail(recipientId, "jugador-" + recipientId + "@chessquery.cl", subject, body);
        saveLog(recipientId, Channel.EMAIL, "player.registered", subject, payload, NotifStatus.SENT);
    }

    /**
     * Notificación de actualización de ELO.
     * Payload: {playerId, oldElo, newElo, delta, ratingType, gameId}
     */
    @Transactional
    public void notifyEloUpdated(Map<String, Object> payload) {
        Long   recipientId = toLong(payload.get("playerId"));
        Object oldElo      = payload.get("oldElo");
        Object newElo      = payload.get("newElo");
        Object delta       = payload.get("delta");

        String subject = "Tu rating ha sido actualizado";
        String body    = String.format(
                "Tu nuevo rating ELO es %s (antes: %s, cambio: %s).",
                newElo, oldElo, delta);

        mockEmailService.sendEmail(recipientId, "jugador-" + recipientId + "@chessquery.cl", subject, body);
        saveLog(recipientId, Channel.EMAIL, "elo.updated", subject, payload, NotifStatus.SENT);
    }

    /**
     * Recordatorio de inicio de ronda.
     * Payload: {tournamentId, roundNumber, startTime, pairingsCount}
     */
    @Transactional
    public void notifyRoundStarting(Map<String, Object> payload) {
        // roundNumber proviene del evento; el recipientId podría ser un broadcast.
        // Aquí notificamos al organizador (recipientId simbólico = tournamentId).
        Long   recipientId  = toLong(payload.get("tournamentId"));
        Object roundNumber  = payload.get("roundNumber");
        Object tournamentId = payload.get("tournamentId");

        String subject = "¡Tu ronda comienza pronto!";
        String body    = String.format(
                "La ronda %s del torneo %s está por comenzar.",
                roundNumber, tournamentId);

        mockEmailService.sendEmail(recipientId, "organizador-" + recipientId + "@chessquery.cl", subject, body);
        saveLog(recipientId, Channel.EMAIL, "tournament.round.starting", subject, payload, NotifStatus.SENT);
    }

    /**
     * Alerta de fallo en sincronización ETL.
     * Payload: {source, status, recordsProcessed, recordsFailed, durationMs, circuitBreakerState}
     */
    @Transactional
    public void notifySyncFailed(Map<String, Object> payload) {
        // recipientId=0 representa al administrador del sistema
        Long   adminId = 0L;
        Object source  = payload.get("source");

        String subject = "ALERTA: Sincronización ETL fallida";
        String body    = String.format(
                "La sincronización de la fuente %s ha fallado. Estado del circuit breaker: %s",
                source, payload.get("circuitBreakerState"));

        mockEmailService.sendEmail(adminId, "admin@chessquery.cl", subject, body);
        saveLog(adminId, Channel.EMAIL, "sync.completed", subject, payload, NotifStatus.SENT);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void saveLog(Long recipientId, Channel channel, String eventType,
                         String subject, Map<String, Object> payload, NotifStatus status) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = payload.toString();
        }

        NotificationLog log = NotificationLog.builder()
                .recipientId(recipientId)
                .channel(channel)
                .eventType(eventType)
                .status(status)
                .subject(subject)
                .payload(payloadJson)
                .sentAt(NotifStatus.SENT.equals(status) ? Instant.now() : null)
                .build();
        notificationLogRepo.save(log);
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }
}
