package cl.chessquery.users.messaging;

import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingHistory;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.RatingHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link EloUpdatedConsumer}.
 *
 * <p>Verifica idempotencia (eventType distinto se ignora), update por rating type,
 * persistencia en rating_history y tolerancia a IDs faltantes.</p>
 */
@ExtendWith(MockitoExtension.class)
class EloUpdatedConsumerTest {

    @Mock private PlayerRepository playerRepo;
    @Mock private RatingHistoryRepository historyRepo;
    @InjectMocks private EloUpdatedConsumer consumer;

    private ChessEvent event(String type, Map<String, Object> payload) {
        ChessEvent e = new ChessEvent();
        e.setEventId("evt-1");
        e.setEventType(type);
        e.setTimestamp(Instant.now());
        e.setPayload(payload);
        return e;
    }

    @Test
    @DisplayName("onEloUpdated_wrongEventType_ignoresAndDoesNotTouchRepo")
    void onEloUpdated_wrongEventType_ignoresAndDoesNotTouchRepo() {
        consumer.onEloUpdated(event("game.finished", Map.of()));
        verify(playerRepo, never()).findById(any());
    }

    @Test
    @DisplayName("onEloUpdated_unknownRatingType_skipsSilently")
    void onEloUpdated_unknownRatingType_skipsSilently() {
        Map<String, Object> payload = Map.of(
                "playerId", 1, "oldElo", 1500, "newElo", 1516, "ratingType", "XYZ");
        consumer.onEloUpdated(event("elo.updated", payload));
        verify(playerRepo, never()).findById(any());
    }

    @Test
    @DisplayName("onEloUpdated_playerNotFound_skipsSilently")
    void onEloUpdated_playerNotFound_skipsSilently() {
        when(playerRepo.findById(eq(99L))).thenReturn(Optional.empty());
        Map<String, Object> payload = Map.of(
                "playerId", 99, "oldElo", 1500, "newElo", 1516, "ratingType", "NATIONAL");
        consumer.onEloUpdated(event("elo.updated", payload));
        verify(historyRepo, never()).save(any());
    }

    @Test
    @DisplayName("onEloUpdated_nationalRating_updatesPlayerAndPersistsHistory")
    void onEloUpdated_nationalRating_updatesPlayerAndPersistsHistory() {
        Player p = Player.builder().eloNational(1500).build();
        p.setId(1L);
        when(playerRepo.findById(eq(1L))).thenReturn(Optional.of(p));
        Map<String, Object> payload = Map.of(
                "playerId", 1, "oldElo", 1500, "newElo", 1516, "ratingType", "NATIONAL");
        consumer.onEloUpdated(event("elo.updated", payload));

        assertThat(p.getEloNational()).isEqualTo(1516);
        ArgumentCaptor<RatingHistory> cap = ArgumentCaptor.forClass(RatingHistory.class);
        verify(historyRepo).save(cap.capture());
        assertThat(cap.getValue().getDelta()).isEqualTo((short) 16);
        assertThat(cap.getValue().getRatingType()).isEqualTo(RatingType.NATIONAL);
    }

    @Test
    @DisplayName("onEloUpdated_fideStandardRating_updatesCorrectField")
    void onEloUpdated_fideStandardRating_updatesCorrectField() {
        Player p = Player.builder().eloFideStandard(1700).build();
        p.setId(2L);
        when(playerRepo.findById(eq(2L))).thenReturn(Optional.of(p));
        Map<String, Object> payload = Map.of(
                "playerId", 2, "oldElo", 1700, "newElo", 1720, "ratingType", "FIDE_STANDARD");
        consumer.onEloUpdated(event("elo.updated", payload));
        assertThat(p.getEloFideStandard()).isEqualTo(1720);
    }

    @Test
    @DisplayName("onEloUpdated_platformRating_updatesPlatform")
    void onEloUpdated_platformRating_updatesPlatform() {
        Player p = Player.builder().eloPlatform(1300).build();
        p.setId(3L);
        when(playerRepo.findById(eq(3L))).thenReturn(Optional.of(p));
        Map<String, Object> payload = Map.of(
                "playerId", 3, "oldElo", 1300, "newElo", 1350, "ratingType", "PLATFORM");
        consumer.onEloUpdated(event("elo.updated", payload));
        assertThat(p.getEloPlatform()).isEqualTo(1350);
    }
}
