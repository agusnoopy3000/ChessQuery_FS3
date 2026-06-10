package cl.chessquery.users.messaging;

import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.ProcessedEvent;
import cl.chessquery.users.repository.ClubRepository;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingUpdatedConsumerTest {

    @Mock PlayerRepository playerRepo;
    @Mock ProcessedEventRepository processedRepo;
    @Mock ClubRepository clubRepo;
    @InjectMocks RatingUpdatedConsumer consumer;

    private static ChessEvent event(String eventId, List<Map<String, Object>> players) {
        ChessEvent e = new ChessEvent();
        e.setEventId(eventId);
        e.setEventType("rating.updated");
        e.setTimestamp(Instant.now());
        e.setPayload(Map.of(
                "source", "AJEFECH",
                "ratingType", "NATIONAL",
                "syncId", 1,
                "players", players
        ));
        return e;
    }

    private static Map<String, Object> payload(String fed, String fide, String rut,
                                                String firstName, String lastName,
                                                Integer eloNat, Integer eloFide) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("federationId", fed);
        m.put("fideId", fide);
        m.put("rut", rut);
        m.put("firstName", firstName);
        m.put("lastName", lastName);
        m.put("eloNational", eloNat);
        m.put("eloFideStandard", eloFide);
        return m;
    }

    @Test
    void matchByFederationIdUpdatesEloAndStampsSource() {
        Player existing = Player.builder()
                .id(1L).firstName("Jorge").lastName("Sepulveda")
                .federationId("738").eloNational(2000)
                .build();
        when(playerRepo.findByFederationId("738")).thenReturn(Optional.of(existing));

        consumer.onRatingUpdated(event(UUID.randomUUID().toString(), List.of(
                payload("738", "3404803", "9914860-8", "Jorge", "Sepulveda", 2053, 2159)
        )));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getEloNational()).isEqualTo(2053);
        assertThat(saved.getEloFideStandard()).isEqualTo(2159);
        assertThat(saved.getEnrichmentSource()).isEqualTo("AJEFECH");
        assertThat(saved.getEnrichedAt()).isNotNull();
        assertThat(saved.getFideId()).isEqualTo("3404803");
        assertThat(saved.getRut()).isEqualTo("9914860-8");
    }

    @Test
    void doesNotOverwriteExistingFideIdRutBirthDate() {
        Player existing = Player.builder()
                .id(2L).firstName("Ana").lastName("Pérez")
                .federationId("99").fideId("CURATED-FIDE").rut("CURATED-RUT")
                .eloNational(1500)
                .build();
        when(playerRepo.findByFederationId("99")).thenReturn(Optional.of(existing));

        consumer.onRatingUpdated(event(UUID.randomUUID().toString(), List.of(
                payload("99", "OTHER-FIDE", "OTHER-RUT", "Ana", "Pérez", 1600, 1700)
        )));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        // ELO siempre se actualiza
        assertThat(saved.getEloNational()).isEqualTo(1600);
        assertThat(saved.getEloFideStandard()).isEqualTo(1700);
        // Pero datos curados se mantienen
        assertThat(saved.getFideId()).isEqualTo("CURATED-FIDE");
        assertThat(saved.getRut()).isEqualTo("CURATED-RUT");
    }

    @Test
    void cascadesToFideIdWhenFederationIdMisses() {
        Player existing = Player.builder().id(3L).firstName("X").lastName("Y").fideId("F1").build();
        when(playerRepo.findByFederationId("zzz")).thenReturn(Optional.empty());
        when(playerRepo.findByFideId("F1")).thenReturn(Optional.of(existing));

        consumer.onRatingUpdated(event(UUID.randomUUID().toString(), List.of(
                payload("zzz", "F1", null, "X", "Y", 1234, null)
        )));

        verify(playerRepo).save(any(Player.class));
    }

    @Test
    void createsNewPlayerWhenNoMatch() {
        when(playerRepo.findByFederationId("nuevo")).thenReturn(Optional.empty());
        when(playerRepo.findByFideId(any())).thenReturn(Optional.empty());
        when(playerRepo.findByRut(any())).thenReturn(Optional.empty());
        when(playerRepo.findByFullNameIgnoreCase(any(), any())).thenReturn(Optional.empty());

        consumer.onRatingUpdated(event(UUID.randomUUID().toString(), List.of(
                payload("nuevo", "F-NEW", "1-9", "Juan", "Soto", 1800, 1900)
        )));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getFederationId()).isEqualTo("nuevo");
        assertThat(saved.getEnrichmentSource()).isEqualTo("AJEFECH");
        assertThat(saved.getEloNational()).isEqualTo(1800);
    }

    @Test
    void idempotencyDiscardsRepeatedEventId() {
        UUID eventId = UUID.randomUUID();
        when(processedRepo.existsById(eventId)).thenReturn(true);

        consumer.onRatingUpdated(event(eventId.toString(), List.of(
                payload("738", null, null, "X", "Y", 1, 1)
        )));

        verify(playerRepo, never()).save(any());
        verify(processedRepo, never()).save(any());
    }

    @Test
    void persistsProcessedEventOnSuccess() {
        UUID eventId = UUID.randomUUID();
        when(processedRepo.existsById(eventId)).thenReturn(false);
        when(playerRepo.findByFederationId("738")).thenReturn(Optional.empty());
        when(playerRepo.findByFullNameIgnoreCase(any(), any())).thenReturn(Optional.empty());

        consumer.onRatingUpdated(event(eventId.toString(), List.of(
                payload("738", null, null, "X", "Y", 1, 1)
        )));

        verify(processedRepo, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    void ignoresWrongEventType() {
        ChessEvent e = new ChessEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setEventType("game.finished");
        e.setPayload(Map.of("foo", "bar"));

        consumer.onRatingUpdated(e);

        verify(playerRepo, never()).save(any());
    }

    private static ChessEvent lichessEvent(String eventId, List<Map<String, Object>> players) {
        ChessEvent e = new ChessEvent();
        e.setEventId(eventId);
        e.setEventType("rating.updated");
        e.setTimestamp(Instant.now());
        e.setPayload(Map.of("source", "LICHESS", "players", players));
        return e;
    }

    private static Map<String, Object> lichessPayload(String username, Integer bullet,
                                                      Integer blitz, Integer rapid, Integer classical) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("lichessUsername", username);
        m.put("eloLichessBullet", bullet);
        m.put("eloLichessBlitz", blitz);
        m.put("eloLichessRapid", rapid);
        m.put("eloLichessClassical", classical);
        m.put("source", "LICHESS");
        return m;
    }

    @Test
    void lichessMatchesByUsernameAndSetsRatingsWithoutTouchingOthers() {
        Player existing = Player.builder()
                .id(9L).firstName("Ana").lastName("López")
                .lichessUsername("anita_cl").eloNational(1500)
                .build();
        when(playerRepo.findByLichessUsername("anita_cl")).thenReturn(Optional.of(existing));

        consumer.onRatingUpdated(lichessEvent(UUID.randomUUID().toString(), List.of(
                lichessPayload("anita_cl", 1800, 1750, 1700, 1650)
        )));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getEloLichessBullet()).isEqualTo(1800);
        assertThat(saved.getEloLichessBlitz()).isEqualTo(1750);
        assertThat(saved.getEloLichessRapid()).isEqualTo(1700);
        assertThat(saved.getEloLichessClassical()).isEqualTo(1650);
        assertThat(saved.getEnrichmentSource()).isEqualTo("LICHESS");
        // No pisa el rating nacional ni otras fuentes
        assertThat(saved.getEloNational()).isEqualTo(1500);
    }

    @Test
    void lichessSkipsWhenUsernameNotRegistered() {
        when(playerRepo.findByLichessUsername("ghost")).thenReturn(Optional.empty());

        consumer.onRatingUpdated(lichessEvent(UUID.randomUUID().toString(), List.of(
                lichessPayload("ghost", 1500, 1500, 1500, 1500)
        )));

        verify(playerRepo, never()).save(any());
    }

    // ── CHESSCOM (mismo contrato que LICHESS: enriquece por username) ─────────

    private static ChessEvent chesscomEvent(String eventId, List<Map<String, Object>> players) {
        ChessEvent e = new ChessEvent();
        e.setEventId(eventId);
        e.setEventType("rating.updated");
        e.setTimestamp(Instant.now());
        e.setPayload(Map.of("source", "CHESSCOM", "players", players));
        return e;
    }

    private static Map<String, Object> chesscomPayload(String username, Integer bullet,
                                                       Integer blitz, Integer rapid, Integer daily) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("chesscomUsername", username);
        m.put("eloChesscomBullet", bullet);
        m.put("eloChesscomBlitz", blitz);
        m.put("eloChesscomRapid", rapid);
        m.put("eloChesscomDaily", daily);
        m.put("source", "CHESSCOM");
        return m;
    }

    @Test
    void chesscomMatchesByUsernameAndSetsRatingsWithoutTouchingOthers() {
        Player existing = Player.builder()
                .id(9L).firstName("Ana").lastName("López")
                .chesscomUsername("anita_chesscom").eloNational(1500)
                .build();
        when(playerRepo.findByChesscomUsername("anita_chesscom")).thenReturn(Optional.of(existing));

        consumer.onRatingUpdated(chesscomEvent(UUID.randomUUID().toString(), List.of(
                chesscomPayload("anita_chesscom", 1900, 1850, 1800, 1600)
        )));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getEloChesscomBullet()).isEqualTo(1900);
        assertThat(saved.getEloChesscomBlitz()).isEqualTo(1850);
        assertThat(saved.getEloChesscomRapid()).isEqualTo(1800);
        assertThat(saved.getEloChesscomDaily()).isEqualTo(1600);
        assertThat(saved.getEnrichmentSource()).isEqualTo("CHESSCOM");
        // No pisa el rating nacional ni otras fuentes
        assertThat(saved.getEloNational()).isEqualTo(1500);
    }

    @Test
    void chesscomSkipsWhenUsernameNotLinked() {
        when(playerRepo.findByChesscomUsername("ghost")).thenReturn(Optional.empty());

        consumer.onRatingUpdated(chesscomEvent(UUID.randomUUID().toString(), List.of(
                chesscomPayload("ghost", 1500, 1500, 1500, 1500)
        )));

        verify(playerRepo, never()).save(any());
    }
}
