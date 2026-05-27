package cl.chessquery.users.service;

import cl.chessquery.users.config.RabbitMQConfig;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
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
 * <p>Verifica routing keys, envelope, payload y que el broker caído no
 * propaga excepciones al caller.</p>
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock private RabbitTemplate rabbit;
    @InjectMocks private EventPublisherService publisher;

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(String routingKey) {
        ArgumentCaptor<Object> cap = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(routingKey), cap.capture());
        return (Map<String, Object>) ((Map<String, Object>) cap.getValue()).get("payload");
    }

    @Test
    @DisplayName("publishUserRegistered_payloadIncludesUserFields")
    void publishUserRegistered_payloadIncludesUserFields() {
        Player p = Player.builder().firstName("Ana").lastName("Soto").email("a@b.cl").build();
        p.setId(7L);
        publisher.publishUserRegistered(p);
        Map<String, Object> pl = payload("user.registered");
        assertThat(pl).containsEntry("userId", 7L).containsEntry("email", "a@b.cl")
                .containsEntry("firstName", "Ana").containsEntry("role", "PLAYER");
    }

    @Test
    @DisplayName("publishUserUpdated_payloadIncludesFieldsChanged")
    void publishUserUpdated_payloadIncludesFieldsChanged() {
        publisher.publishUserUpdated(1L, List.of("firstName", "region"));
        assertThat(payload("user.updated"))
                .containsEntry("userId", 1L)
                .containsEntry("fieldsChanged", List.of("firstName", "region"));
    }

    @Test
    @DisplayName("publishEloUpdated_normalCase_payloadIncludesDelta")
    void publishEloUpdated_normalCase_payloadIncludesDelta() {
        publisher.publishEloUpdated(1L, 1500, 1516, RatingType.NATIONAL, 99L);
        assertThat(payload("elo.updated"))
                .containsEntry("delta", 16)
                .containsEntry("ratingType", "NATIONAL")
                .containsEntry("gameId", 99L);
    }

    @Test
    @DisplayName("publishEloUpdated_nullGameId_usesEmptyString")
    void publishEloUpdated_nullGameId_usesEmptyString() {
        publisher.publishEloUpdated(1L, 1500, 1490, RatingType.FIDE_STANDARD, null);
        assertThat(payload("elo.updated")).containsEntry("gameId", "");
    }

    @Test
    @DisplayName("publish_brokerThrows_doesNotPropagate")
    void publish_brokerThrows_doesNotPropagate() {
        doThrow(new AmqpException("down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
        assertThatNoException().isThrownBy(() ->
                publisher.publishEloUpdated(1L, 1500, 1500, RatingType.NATIONAL, null));
    }
}
