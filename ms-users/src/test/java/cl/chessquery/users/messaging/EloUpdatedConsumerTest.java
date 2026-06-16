package cl.chessquery.users.messaging;

import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingHistory;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.RatingHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    @ParameterizedTest
    @EnumSource(RatingType.class)
    @DisplayName("onEloUpdated_allRatingTypes_updateCorrectField")
    void onEloUpdated_allRatingTypes_updateCorrectField(RatingType type) {
        Player p = Player.builder().build();
        p.setId(7L);
        when(playerRepo.findById(eq(7L))).thenReturn(Optional.of(p));
        Map<String, Object> payload = Map.of(
                "playerId", 7, "oldElo", 1000, "newElo", 1234, "ratingType", type.name());

        consumer.onEloUpdated(event("elo.updated", payload));

        assertThat(getElo(p, type)).isEqualTo(1234); // applyElo escribió el campo correcto
        verify(historyRepo).save(any(RatingHistory.class));
    }

    @Test
    @DisplayName("onEloUpdated_stringNumbers_parsedViaToLongToInt")
    void onEloUpdated_stringNumbers_parsedViaToLongToInt() {
        // playerId/elos llegan como String (p. ej. desde JSON laxo) → toLong/toInt los parsean.
        Player p = Player.builder().eloNational(1400).build();
        p.setId(5L);
        when(playerRepo.findById(eq(5L))).thenReturn(Optional.of(p));
        Map<String, Object> payload = Map.of(
                "playerId", "5", "oldElo", "1400", "newElo", "1450", "ratingType", "NATIONAL");

        consumer.onEloUpdated(event("elo.updated", payload));

        assertThat(p.getEloNational()).isEqualTo(1450);
    }

    @Test
    @DisplayName("onEloUpdated_nullTimestamp_recordsWithNow")
    void onEloUpdated_nullTimestamp_recordsWithNow() {
        Player p = Player.builder().eloNational(1500).build();
        p.setId(8L);
        when(playerRepo.findById(eq(8L))).thenReturn(Optional.of(p));
        ChessEvent e = new ChessEvent();
        e.setEventId("evt-x");
        e.setEventType("elo.updated");
        e.setTimestamp(null); // sin timestamp → usa Instant.now()
        e.setPayload(Map.of("playerId", 8, "oldElo", 1500, "newElo", 1510, "ratingType", "NATIONAL"));

        consumer.onEloUpdated(e);

        ArgumentCaptor<RatingHistory> cap = ArgumentCaptor.forClass(RatingHistory.class);
        verify(historyRepo).save(cap.capture());
        assertThat(cap.getValue().getRecordedAt()).isNotNull();
    }

    /** Espejo del switch de applyElo, para verificar el campo escrito por tipo. */
    private static Integer getElo(Player p, RatingType type) {
        return switch (type) {
            case NATIONAL          -> p.getEloNational();
            case FIDE_STANDARD     -> p.getEloFideStandard();
            case FIDE_RAPID        -> p.getEloFideRapid();
            case FIDE_BLITZ        -> p.getEloFideBlitz();
            case PLATFORM          -> p.getEloPlatform();
            case LICHESS_BULLET    -> p.getEloLichessBullet();
            case LICHESS_BLITZ     -> p.getEloLichessBlitz();
            case LICHESS_RAPID     -> p.getEloLichessRapid();
            case LICHESS_CLASSICAL -> p.getEloLichessClassical();
        };
    }
}
