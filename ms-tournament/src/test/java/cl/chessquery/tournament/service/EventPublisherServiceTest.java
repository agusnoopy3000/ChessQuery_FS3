package cl.chessquery.tournament.service;

import cl.chessquery.tournament.config.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@link EventPublisherService}.
 *
 * <p>Verifica que cada método publica con el routing key correcto y un envelope
 * con eventId/eventType/timestamp/payload. Una excepción del broker no
 * propaga al caller (publicar es best-effort).</p>
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock private RabbitTemplate rabbit;
    @InjectMocks private EventPublisherService publisher;

    @SuppressWarnings("unchecked")
    private Map<String, Object> capture(String routingKey) {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(routingKey), cap.capture());
        return (Map<String, Object>) cap.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String routingKey) {
        return (Map<String, Object>) capture(routingKey).get("payload");
    }

    @Test
    @DisplayName("publishTournamentCreated_payloadFields")
    void publishTournamentCreated_payloadFields() {
        publisher.publishTournamentCreated(1L, "Open", 9L, "SWISS");
        assertThat(payload("tournament.created"))
                .containsEntry("tournamentId", 1L)
                .containsEntry("name", "Open")
                .containsEntry("organizerId", 9L)
                .containsEntry("format", "SWISS");
    }

    @Test
    @DisplayName("publishPlayerRegistered_nullRating_defaultsToZero")
    void publishPlayerRegistered_nullRating_defaultsToZero() {
        publisher.publishPlayerRegistered(1L, 2L, null, null);
        Map<String, Object> p = payload("player.registered");
        assertThat(p).containsEntry("seedRating", 0)
                .doesNotContainKey("tournamentName");
    }

    @Test
    @DisplayName("publishPlayerRegistered_withTournamentName_payloadIncludesIt")
    void publishPlayerRegistered_withTournamentName_payloadIncludesIt() {
        publisher.publishPlayerRegistered(1L, 2L, 1500, "Open");
        assertThat(payload("player.registered")).containsEntry("tournamentName", "Open");
    }

    @Test
    @DisplayName("publishRegistrationPending_payloadFields")
    void publishRegistrationPending_payloadFields() {
        publisher.publishRegistrationPending(1L, 2L, 9L, "Open");
        assertThat(payload("registration.pending"))
                .containsEntry("organizerId", 9L)
                .containsEntry("tournamentName", "Open");
    }

    @Test
    @DisplayName("publishRegistrationApproved_payloadFields")
    void publishRegistrationApproved_payloadFields() {
        publisher.publishRegistrationApproved(1L, 2L, "Open");
        assertThat(payload("registration.approved")).containsEntry("playerId", 2L);
    }

    @Test
    @DisplayName("publishRegistrationRejected_nullReason_defaultsToEmptyString")
    void publishRegistrationRejected_nullReason_defaultsToEmptyString() {
        publisher.publishRegistrationRejected(1L, 2L, "Open", null);
        assertThat(payload("registration.rejected")).containsEntry("reason", "");
    }

    @Test
    @DisplayName("publishRoundStarting_payloadFields")
    void publishRoundStarting_payloadFields() {
        publisher.publishRoundStarting(1L, 3, 7);
        assertThat(payload("tournament.round.starting"))
                .containsEntry("roundNumber", 3)
                .containsEntry("pairingsCount", 7);
    }

    @Test
    @DisplayName("publishGameFinished_payloadFields")
    void publishGameFinished_payloadFields() {
        publisher.publishGameFinished(10L, 1L, 2L, 3L, "1-0");
        assertThat(payload("game.finished"))
                .containsEntry("result", "1-0")
                .containsEntry("pairingId", 10L);
    }

    @Test
    @DisplayName("publish_brokerThrows_doesNotPropagate")
    void publish_brokerThrows_doesNotPropagate() {
        doThrow(new AmqpException("down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThatNoException().isThrownBy(() ->
                publisher.publishTournamentCreated(1L, "X", 1L, "SWISS"));
    }
}
