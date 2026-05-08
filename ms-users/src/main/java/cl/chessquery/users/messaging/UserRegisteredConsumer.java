package cl.chessquery.users.messaging;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.dto.ProvisionPlayerRequest;
import cl.chessquery.users.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Consumidor del evento user.registered publicado por el API Gateway tras
 * el webhook de Supabase Auth (auth.users.created).
 *
 * Delega la lógica de creación/asociación a {@link PlayerService#provisionBySupabaseId}
 * para mantener single source of truth con el endpoint {@code POST /users/provision}
 * (que se invoca cuando el webhook no llegó o un JWT entra antes que el evento Rabbit).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final PlayerService playerService;

    @RabbitListener(queues = RabbitMQConfig.USERS_REGISTRATION_QUEUE)
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

        ProvisionPlayerRequest req = new ProvisionPlayerRequest(
                supabaseUserId,
                (String) payload.get("email"),
                (String) payload.getOrDefault("firstName", null),
                (String) payload.getOrDefault("lastName", null),
                (String) payload.getOrDefault("lichessUsername", null),
                (String) payload.getOrDefault("clubName", null)
        );
        playerService.provisionBySupabaseId(req);
    }
}
