package cl.chessquery.users.messaging;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {

    private final PlayerRepository playerRepository;

    @RabbitListener(queues = RabbitMQConfig.USERS_REGISTERED_QUEUE)
    @Transactional
    public void onUserRegistered(ChessEvent event) {
        if (!"user.registered".equals(event.getEventType())) {
            log.debug("Evento ignorado en users.registered.queue: {}", event.getEventType());
            return;
        }

        Map<String, Object> payload = event.getPayload();
        Long userId = toLong(payload.get("userId"));
        String email = toStringValue(payload.get("email"));
        String firstName = toStringValue(payload.get("firstName"));
        String lastName = toStringValue(payload.get("lastName"));
        String role = toStringValue(payload.get("role"));

        if (!"PLAYER".equals(role)) {
            log.debug("user.registered ignorado para role={}", role);
            return;
        }

        if (playerRepository.existsById(userId)) {
            log.debug("Perfil player ya existe para userId={}", userId);
            return;
        }

        if (email != null && playerRepository.findByEmail(email).isPresent()) {
            log.warn("Ya existe un player con email={} al procesar user.registered para userId={}", email, userId);
            return;
        }

        playerRepository.insertProvisionedPlayer(
                userId,
                sanitizeName(firstName, "Jugador"),
                sanitizeName(lastName, "Nuevo"),
                email
        );
        playerRepository.syncIdSequence();

        log.info("Perfil player provisionado para userId={} ({})", userId, email);
    }

    private static String sanitizeName(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static String toStringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
