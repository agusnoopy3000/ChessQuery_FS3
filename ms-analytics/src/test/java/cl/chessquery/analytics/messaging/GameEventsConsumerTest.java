package cl.chessquery.analytics.messaging;

import cl.chessquery.analytics.entity.GameRecord;
import cl.chessquery.analytics.entity.PlayerStatsMV;
import cl.chessquery.analytics.entity.ProcessedEvent;
import cl.chessquery.analytics.repository.GameRecordRepository;
import cl.chessquery.analytics.repository.PlayerStatsMVRepository;
import cl.chessquery.analytics.repository.ProcessedEventRepository;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameEventsConsumerTest {

    @Mock
    private PlayerStatsMVRepository  playerStatsRepo;
    @Mock
    private GameRecordRepository     gameRecordRepo;
    @Mock
    private ProcessedEventRepository processedEventRepo;
    @Mock
    private Channel                  channel;

    @InjectMocks
    private GameEventsConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(processedEventRepo.existsById(any())).thenReturn(false);
        lenient().when(processedEventRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(gameRecordRepo.findById(any())).thenReturn(Optional.empty());
        lenient().when(gameRecordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(playerStatsRepo.findById(any())).thenReturn(Optional.empty());
        lenient().when(playerStatsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void onGameEvent_gameFinished_savesGameRecordAndBothPlayerStats() throws IOException {
        // Arrange
        ChessEvent event = new ChessEvent();
        event.setEventId("uuid-test-001");
        event.setEventType("game.finished");
        event.setPayload(Map.of(
                "gameId",            4521,
                "whitePlayerId",     12,
                "blackPlayerId",     34,
                "result",            "1-0",
                "gameType",          "TOURNAMENT",
                "openingId",         15,
                "totalMoves",        42,
                "tournamentPairingId", 89
        ));

        // Act
        consumer.onGameEvent(event, channel, 1L);

        // Assert — se guardan: GameRecord + 2 PlayerStatsMV + 1 ProcessedEvent
        verify(gameRecordRepo, times(1)).save(any(GameRecord.class));
        verify(playerStatsRepo, times(2)).save(any(PlayerStatsMV.class));
        verify(processedEventRepo, times(1)).save(any(ProcessedEvent.class));
        verify(channel, times(1)).basicAck(1L, false);
    }

    @Test
    void onGameEvent_duplicateEvent_isIgnored() throws IOException {
        // Arrange
        when(processedEventRepo.existsById("uuid-dup")).thenReturn(true);

        ChessEvent event = new ChessEvent();
        event.setEventId("uuid-dup");
        event.setEventType("game.finished");
        event.setPayload(Map.of(
                "gameId", 1, "whitePlayerId", 1, "blackPlayerId", 2,
                "result", "1-0", "totalMoves", 30
        ));

        // Act
        consumer.onGameEvent(event, channel, 2L);

        // Assert — no se guarda nada de negocio
        verify(gameRecordRepo, never()).save(any());
        verify(playerStatsRepo, never()).save(any());
        verify(channel, times(1)).basicAck(2L, false);
    }

    @Test
    void onGameEvent_eloUpdated_updatesBestElo() throws IOException {
        // Arrange
        PlayerStatsMV existing = PlayerStatsMV.builder()
                .playerId(12L)
                .bestElo(1750)
                .build();
        when(playerStatsRepo.findById(12L)).thenReturn(Optional.of(existing));

        ChessEvent event = new ChessEvent();
        event.setEventId("uuid-elo-001");
        event.setEventType("elo.updated");
        event.setPayload(Map.of(
                "playerId",    12,
                "oldElo",      1750,
                "newElo",      1762,
                "delta",       12,
                "ratingType",  "FIDE_STANDARD",
                "gameId",      4521
        ));

        // Act
        consumer.onGameEvent(event, channel, 3L);

        // Assert
        ArgumentCaptor<PlayerStatsMV> captor = ArgumentCaptor.forClass(PlayerStatsMV.class);
        verify(playerStatsRepo, times(1)).save(captor.capture());
        assertThat(captor.getValue().getBestElo()).isEqualTo(1762);
        verify(channel, times(1)).basicAck(3L, false);
    }
}
