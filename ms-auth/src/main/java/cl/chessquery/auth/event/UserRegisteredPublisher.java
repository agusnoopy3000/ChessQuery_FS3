package cl.chessquery.auth.event;

import cl.chessquery.auth.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(Long userId, String email, String firstName, String lastName, String role) {
        Map<String, Object> payload = Map.of(
                "userId", userId,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "role", role
        );

        Map<String, Object> event = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "user.registered",
                "timestamp", Instant.now().toString(),
                "payload", payload
        );

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "user.registered", event);
            log.debug("Evento user.registered publicado para userId={}", userId);
        } catch (Exception e) {
            log.error("No se pudo publicar user.registered para userId={}: {}", userId, e.getMessage());
        }
    }
}
