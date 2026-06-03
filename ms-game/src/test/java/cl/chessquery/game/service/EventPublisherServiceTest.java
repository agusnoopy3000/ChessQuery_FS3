package cl.chessquery.game.service;

import cl.chessquery.game.config.RabbitMQConfig;
import cl.chessquery.game.entity.Game;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.entity.Opening;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@link EventPublisherService}.
 *
 * <p>Mockea {@link RabbitTemplate} para verificar que cada método publica
 * con el routing key correcto y un payload con los campos esperados.</p>
 *
 * <p>Invariantes verificados:
 * <ul>
 *   <li>Todos los eventos van al exchange {@link RabbitMQConfig#EXCHANGE}.</li>
 *   <li>Cada evento envuelto incluye eventId, eventType, timestamp y payload.</li>
 *   <li>Una excepción del broker NO se propaga (publicar es best-effort).</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock private RabbitTemplate rabbit;

    @InjectMocks private EventPublisherService publisher;

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePublishedEvent(String expectedRoutingKey) {
        ArgumentCaptor<Object> bodyCap = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq(expectedRoutingKey), bodyCap.capture());
        return (Map<String, Object>) bodyCap.getValue();
    }

    @Nested
    @DisplayName("publishGameFinished")
    class GameFinished {

        @Test
        @DisplayName("publishGameFinished_completeGame_envelopeAndPayloadIncludeAllFields")
        void publishGameFinished_completeGame_envelopeAndPayloadIncludeAllFields() {
            Opening opening = Opening.builder().id(7).ecoCode("C20").name("KP").build();
            Game game = Game.builder()
                    .whitePlayerId(1L).blackPlayerId(2L)
                    .result("1-0").gameType(GameType.TOURNAMENT)
                    .whiteEloBefore(1500).blackEloBefore(1500)
                    .whiteEloAfter(1516).blackEloAfter(1484)
                    .totalMoves(42)
                    .opening(opening)
                    .build();
            game.setId(99L);

            publisher.publishGameFinished(game);

            Map<String, Object> event = capturePublishedEvent("game.finished");
            assertThat(event).containsKeys("eventId", "eventType", "timestamp", "payload");
            assertThat(event.get("eventType")).isEqualTo("game.finished");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("gameId", 99L)
                    .containsEntry("whitePlayerId", 1L)
                    .containsEntry("blackPlayerId", 2L)
                    .containsEntry("result", "1-0")
                    .containsEntry("gameType", "TOURNAMENT")
                    .containsEntry("whiteEloBefore", 1500)
                    .containsEntry("blackEloBefore", 1500)
                    .containsEntry("whiteEloAfter", 1516)
                    .containsEntry("blackEloAfter", 1484)
                    .containsEntry("totalMoves", 42)
                    .containsEntry("openingId", 7);
        }

        @Test
        @DisplayName("publishGameFinished_nullElos_payloadDefaultsToZero")
        void publishGameFinished_nullElos_payloadDefaultsToZero() {
            Game game = Game.builder()
                    .whitePlayerId(1L).blackPlayerId(2L)
                    .result("0-1").gameType(GameType.CASUAL)
                    .build();
            game.setId(5L);

            publisher.publishGameFinished(game);

            Map<String, Object> event = capturePublishedEvent("game.finished");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("whiteEloBefore", 0)
                    .containsEntry("blackEloBefore", 0)
                    .containsEntry("whiteEloAfter", 0)
                    .containsEntry("blackEloAfter", 0)
                    .containsEntry("totalMoves", 0)
                    .doesNotContainKey("openingId");
        }
    }

    @Nested
    @DisplayName("publishGameInvitation")
    class Invitation {

        @Test
        @DisplayName("publishGameInvitation_allFieldsProvided_payloadMatches")
        void publishGameInvitation_allFieldsProvided_payloadMatches() {
            publisher.publishGameInvitation(10L, 20L, 30L, "Magnus", "https://x/game/10", "rival@chessquery.cl");

            Map<String, Object> event = capturePublishedEvent("game.invitation");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("gameId", 10L)
                    .containsEntry("playerId", 20L)
                    .containsEntry("inviterId", 30L)
                    .containsEntry("inviterName", "Magnus")
                    .containsEntry("gameUrl", "https://x/game/10")
                    .containsEntry("email", "rival@chessquery.cl");
        }

        @Test
        @DisplayName("publishGameInvitation_nullOptionalFields_payloadDefaultsApplied")
        void publishGameInvitation_nullOptionalFields_payloadDefaultsApplied() {
            publisher.publishGameInvitation(10L, 20L, null, null, null, null);

            Map<String, Object> event = capturePublishedEvent("game.invitation");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("inviterId", 0L)
                    .containsEntry("inviterName", "")
                    .containsEntry("gameUrl", "")
                    .containsEntry("email", "");
        }
    }

    @Nested
    @DisplayName("publishEloUpdated")
    class EloUpdated {

        @Test
        @DisplayName("publishEloUpdated_normalCase_payloadIncludesDelta")
        void publishEloUpdated_normalCase_payloadIncludesDelta() {
            publisher.publishEloUpdated(1L, 1500, 1516, 99L, "NATIONAL");

            Map<String, Object> event = capturePublishedEvent("elo.updated");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("playerId", 1L)
                    .containsEntry("oldElo", 1500)
                    .containsEntry("newElo", 1516)
                    .containsEntry("delta", 16)
                    .containsEntry("ratingType", "NATIONAL")
                    .containsEntry("gameId", 99L);
        }

        @Test
        @DisplayName("publishEloUpdated_nullGameId_payloadUsesEmptyString")
        void publishEloUpdated_nullGameId_payloadUsesEmptyString() {
            publisher.publishEloUpdated(2L, 1500, 1490, null, "FIDE");

            Map<String, Object> event = capturePublishedEvent("elo.updated");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            assertThat(payload).containsEntry("delta", -10).containsEntry("gameId", "");
        }
    }

    @Test
    @DisplayName("publish_brokerThrows_doesNotPropagate")
    void publish_brokerThrows_doesNotPropagate() {
        doThrow(new AmqpException("broker down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatNoException().isThrownBy(() ->
                publisher.publishEloUpdated(1L, 1500, 1500, 1L, "NATIONAL"));
        verify(rabbit, times(1)).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }
}
