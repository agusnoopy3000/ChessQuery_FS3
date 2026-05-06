package cl.chessquery.gateway.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento user.registered publicado a RabbitMQ.
 * Mantiene el formato existente definido en CONTEXT.md para compatibilidad
 * con los consumidores downstream (MS-Users, MS-Notifications).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("payload")
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        @JsonProperty("userId")
        private String userId;

        @JsonProperty("email")
        private String email;

        @JsonProperty("role")
        private String role;

        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("lastName")
        private String lastName;
    }
}
