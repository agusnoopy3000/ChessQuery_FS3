package cl.chessquery.users.messaging;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumidor del evento user.registered publicado por el API Gateway tras
 * el webhook de Supabase Auth (auth.users.created).
 *
 * Payload esperado (formato CONTEXT.md):
 * {
 *   "eventId":   "uuid-v4",
 *   "eventType": "user.registered",
 *   "timestamp": "ISO-8601",
 *   "payload": {
 *     "userId":    "<supabase-uuid>",
 *     "email":     "user@example.com",
 *     "role":      "PLAYER" | "ORGANIZER" | "ADMIN",
 *     "firstName": "...",
 *     "lastName":  "..."
 *   }
 * }
 *
 * Acción: si ya existe un Player con ese supabaseUserId → idempotente (no-op).
 * Si no existe pero hay match por email → asocia supabaseUserId al Player
 * existente. Si no, crea un nuevo Player.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final PlayerRepository playerRepo;

    @RabbitListener(queues = RabbitMQConfig.USERS_REGISTRATION_QUEUE)
    @Transactional
    public void onUserRegistered(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        if (payload == null) {
            log.warn("user.registered recibido sin payload: {}", message);
            return;
        }

        String userIdStr = (String) payload.get("userId");
        if (!StringUtils.hasText(userIdStr)) {
            log.warn("user.registered sin userId, descartado");
            return;
        }

        UUID supabaseUserId;
        try {
            supabaseUserId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException ex) {
            log.warn("user.registered con userId inválido: {}", userIdStr);
            return;
        }

        String email     = (String) payload.get("email");
        String firstName = (String) payload.getOrDefault("firstName", null);
        String lastName  = (String) payload.getOrDefault("lastName", null);

        // 1. Idempotencia: si ya existe Player con este supabaseUserId, no-op.
        if (playerRepo.findBySupabaseUserId(supabaseUserId).isPresent()) {
            log.debug("user.registered ignorado (Player ya existe para supabaseUserId={})",
                    supabaseUserId);
            return;
        }

        // 2. Match por email: asociar supabaseUserId a Player existente.
        if (StringUtils.hasText(email)) {
            Player existing = playerRepo.findByEmail(email).orElse(null);
            if (existing != null) {
                existing.setSupabaseUserId(supabaseUserId);
                playerRepo.save(existing);
                log.info("user.registered: asociado supabaseUserId={} a Player existente id={}",
                        supabaseUserId, existing.getId());
                return;
            }
        }

        // 3. Crear nuevo Player.
        Instant now = Instant.now();
        Player p = Player.builder()
                .firstName(StringUtils.hasText(firstName) ? firstName : "Jugador")
                .lastName(StringUtils.hasText(lastName)  ? lastName  : userIdStr.substring(0, 8))
                .email(email)
                .supabaseUserId(supabaseUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        playerRepo.save(p);
        log.info("user.registered: Player creado id={} supabaseUserId={} email={}",
                p.getId(), supabaseUserId, email);
    }
}
