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
    private final PlayerNameResolver         playerNameResolver;

    /**
     * Email de bienvenida cuando un usuario se registra.
     * Payload: {userId, email, firstName, lastName, role}
     */
    @Transactional
    public void notifyWelcome(Map<String, Object> payload) {
        // En el flujo Supabase Auth, userId llega como UUID y el Player
        // numérico aún no existe. tryToLong devuelve null en ese caso;
        // el log queda con recipient_id=NULL y la fila in-app se omite
        // (no hay destinatario al que la campana le sirva).
        Long   recipientId = tryToLong(payload.get("userId"));
        String email       = (String) payload.get("email");
        String firstName   = (String) payload.get("firstName");

        String greeting = (firstName != null && !firstName.isBlank()) ? firstName : "jugador";
        String subject = "¡Bienvenido a ChessQuery!";
        String body    = String.format(
                "Hola %s, tu cuenta fue creada exitosamente. ¡Bienvenido a ChessQuery!",
                greeting);

        if (email != null && !email.isBlank()) {
            mockEmailService.sendEmail(recipientId, email, subject, body);
        }
        saveLog(recipientId, Channel.EMAIL, "user.registered", subject, payload, NotifStatus.SENT);
        if (recipientId != null) {
            saveLog(recipientId, Channel.IN_APP, "user.registered",
                    "¡Bienvenido a ChessQuery!", payload, NotifStatus.SENT);
        }
    }

    /**
     * Confirmación de inscripción en torneo.
     * Payload: {playerId, tournamentId, seedRating, registeredAt}
     */
    @Transactional
    public void notifyRegistration(Map<String, Object> payload) {
        Long recipientId  = toLong(payload.get("playerId"));
        Object tournamentId   = payload.get("tournamentId");
        Object tournamentName = payload.getOrDefault("tournamentName", "el torneo");
        Object seedRating     = payload.get("seedRating");

        String subject = String.format("Inscripción confirmada en %s", tournamentName);
        String body    = String.format(
                "Te has inscrito en %s con rating de seed %s.",
                tournamentName, seedRating);

        // Solo notificación in-app (sin email; los únicos emails son bienvenida e invitación).
        saveLog(recipientId, Channel.IN_APP, "player.registered",
                String.format("Estás inscrito en %s", tournamentName),
                payload, NotifStatus.SENT);
        // Evitar log adicional vacío que decía "torneo %s" con ID raw.
        if (tournamentName.equals("el torneo")) {
            log.debug("player.registered sin tournamentName en payload (tournamentId={})", tournamentId);
        }
    }

    /**
     * Notificación al ORGANIZADOR cuando llega una nueva inscripción que requiere
     * aprobación. Payload: {tournamentId, tournamentName, playerId, organizerId}
     */
    @Transactional
    public void notifyRegistrationPending(Map<String, Object> payload) {
        Long organizerId = toLong(payload.get("organizerId"));
        Long playerId    = toLong(payload.get("playerId"));
        Object tournamentName = payload.getOrDefault("tournamentName", "tu torneo");
        if (organizerId == null) {
            log.debug("registration.pending sin organizerId, ignorado");
            return;
        }
        String playerName = playerNameResolver.resolve(playerId);
        String msg = String.format("Nueva inscripción de %s en %s — requiere tu aprobación",
                playerName, tournamentName);
        saveLog(organizerId, Channel.IN_APP, "registration.pending", msg, payload, NotifStatus.SENT);
    }

    /**
     * Notificación al JUGADOR cuando su inscripción es aprobada por el organizador.
     * Payload: {tournamentId, tournamentName, playerId}
     */
    @Transactional
    public void notifyRegistrationApproved(Map<String, Object> payload) {
        Long recipientId = toLong(payload.get("playerId"));
        Object tournamentName = payload.getOrDefault("tournamentName", "el torneo");
        if (recipientId == null) return;
        String subject = "Tu inscripción fue aprobada";
        String body    = String.format("Tu inscripción al torneo %s fue aprobada. ¡Nos vemos pronto!",
                tournamentName);
        saveLog(recipientId, Channel.IN_APP, "registration.approved",
                String.format("¡Aprobado! Estás dentro de %s", tournamentName),
                payload, NotifStatus.SENT);
    }

    /**
     * Notificación al JUGADOR cuando su inscripción es rechazada.
     * Payload: {tournamentId, tournamentName, playerId, reason}
     */
    @Transactional
    public void notifyRegistrationRejected(Map<String, Object> payload) {
        Long recipientId = toLong(payload.get("playerId"));
        Object tournamentName = payload.getOrDefault("tournamentName", "el torneo");
        Object reason = payload.getOrDefault("reason", "");
        if (recipientId == null) return;
        String subject = "Tu inscripción fue rechazada";
        String reasonSuffix = (reason instanceof String s && !s.isBlank()) ? " — Motivo: " + s : "";
        String body = String.format("Tu inscripción al torneo %s fue rechazada por el organizador.%s",
                tournamentName, reasonSuffix);
        saveLog(recipientId, Channel.IN_APP, "registration.rejected",
                String.format("Inscripción rechazada en %s%s", tournamentName, reasonSuffix),
                payload, NotifStatus.SENT);
    }

    /**
     * Notificación de invitación a partida (push in-app + email vía Supabase OTP).
     * Payload: {gameId, playerId, inviterId, inviterName, gameUrl}
     *
     * El email magic link lo manda el frontend directamente vía Supabase Auth.
     * Acá solo creamos la notificación in-app para que la campana del usuario
     * invitado emita el toast emergente automáticamente (polling 8s).
     */
    @Transactional
    public void notifyGameInvitation(Map<String, Object> payload) {
        Long recipientId = toLong(payload.get("playerId"));
        Object gameId = payload.get("gameId");
        Object inviterName = payload.getOrDefault("inviterName", "tu rival");
        String email = (payload.get("email") instanceof String s && !s.isBlank()) ? s.trim() : null;
        String gameUrl = (payload.get("gameUrl") instanceof String u && !u.isBlank()) ? u.trim() : null;

        // Email al correo invitado (sea jugador registrado o no). Sólo se envía
        // de verdad si hay SMTP configurado; si no, queda en log (no rompe nada).
        if (email != null) {
            String emailSubject = "Te invitan a una partida en ChessQuery";
            String emailBody = String.format(
                    "%s te invitó a jugar una partida en ChessQuery.%s",
                    inviterName,
                    gameUrl != null ? "\n\nUnite a la partida:\n" + gameUrl : "");
            mockEmailService.sendEmail(recipientId, email, emailSubject, emailBody);
        }

        // Notificación in-app sólo si el invitado tiene cuenta (playerId).
        if (recipientId != null) {
            String body = String.format("%s te invitó a jugar (partida #%s).", inviterName, gameId);
            saveLog(recipientId, Channel.IN_APP, "game.invitation", body, payload, NotifStatus.SENT);
            log.info("game.invitation: notificación in-app creada para player {} (game {})",
                    recipientId, gameId);
        } else if (email == null) {
            log.debug("game.invitation sin playerId ni email, ignorado");
        }
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

        saveLog(recipientId, Channel.IN_APP, "elo.updated",
                String.format("ELO: %s → %s", oldElo, newElo),
                payload, NotifStatus.SENT);
    }

    /**
     * N1+N2: notificación al finalizar una partida live.
     * Payload esperado: {whitePlayerId, blackPlayerId, result, endReason, finalizedGameId}
     * Persiste IN_APP y dispara EMAIL para ambos jugadores.
     */
    @Transactional
    public void notifyGameFinished(Map<String, Object> payload) {
        Long whiteId = toLong(payload.get("whitePlayerId"));
        Long blackId = toLong(payload.get("blackPlayerId"));
        String result = String.valueOf(payload.getOrDefault("result", ""));
        Object gameId = payload.getOrDefault("gameId", payload.get("finalizedGameId"));
        notifyOneFinished(whiteId, blackId, "white", result, gameId, payload);
        notifyOneFinished(blackId, whiteId, "black", result, gameId, payload);
    }

    private void notifyOneFinished(Long recipientId, Long opponentId, String myColor,
                                   String result, Object gameId, Map<String, Object> payload) {
        Object finalizedGameId = gameId;
        if (recipientId == null) return;
        String outcome = describeResult(result, myColor);
        String opponentName = playerNameResolver.resolve(opponentId);
        // El resultado de torneo grabado por el organizador llega sin gameId
        // (no hubo partida en vivo). En ese caso evitamos imprimir "#null".
        boolean hasGameId = finalizedGameId != null && !"null".equals(String.valueOf(finalizedGameId));
        String gameRef = hasGameId ? " #" + finalizedGameId : "";
        String subject = String.format("Partida%s finalizada · %s", gameRef, outcome);
        String pgnNote = hasGameId ? String.format(" PGN guardado como game #%s.", finalizedGameId) : "";
        String body = String.format(
                "Tu partida contra %s terminó: %s. Resultado %s.%s",
                opponentName, outcome, result, pgnNote);
        saveLog(recipientId, Channel.IN_APP, "game.finished", subject, payload, NotifStatus.SENT);
    }

    /**
     * N1: notificación al crear torneo (visible al organizador).
     */
    @Transactional
    public void notifyTournamentCreated(Map<String, Object> payload) {
        Long organizerId = toLong(payload.get("organizerId"));
        if (organizerId == null) return;
        Object tournamentId = payload.get("tournamentId");
        Object name = payload.getOrDefault("name", "Torneo " + tournamentId);
        String subject = String.format("Torneo \"%s\" creado", name);
        saveLog(organizerId, Channel.IN_APP, "tournament.created", subject, payload, NotifStatus.SENT);
    }

    private static String describeResult(String result, String myColor) {
        if ("1/2-1/2".equals(result)) return "Tablas";
        if ("1-0".equals(result)) return "white".equals(myColor) ? "Ganaste" : "Perdiste";
        if ("0-1".equals(result)) return "black".equals(myColor) ? "Ganaste" : "Perdiste";
        return "Finalizada";
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

        // Sin email (los únicos correos son bienvenida e invitación). Solo log;
        // a los jugadores se les notifica el inicio de ronda vía game.invitation.
        log.info("tournament.round.starting: ronda {} del torneo {}", roundNumber, tournamentId);
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

        // Sin email: alerta de ETL solo a log (los únicos correos son bienvenida e invitación).
        log.warn("sync.completed FALLIDA: fuente={} circuitBreaker={}",
                source, payload.get("circuitBreakerState"));
        if (adminId == null) return; // defensivo
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
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    /**
     * Variante tolerante: devuelve null si {@code v} no es numérico (típicamente
     * cuando llega un UUID de Supabase en vez del Player id). El caller decide
     * si persistir el log con recipientId null o saltarlo.
     */
    private static Long tryToLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
