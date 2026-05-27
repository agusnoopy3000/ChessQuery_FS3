package cl.chessquery.users.service;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.entity.Club;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingHistory;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.exception.ApiException;
import cl.chessquery.users.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link PlayerService}.
 *
 * <p>Mockea repositorios, EventPublisherService y EntityManager. Los flujos
 * que dependen de native queries (sync/provision con INSERT…ON CONFLICT,
 * UPDATE id) se cubren parcialmente — el resto es lógica pura del service.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock private PlayerRepository playerRepo;
    @Mock private ClubRepository clubRepo;
    @Mock private RatingHistoryRepository historyRepo;
    @Mock private PlayerTitleHistoryRepository titleRepo;
    @Mock private EventPublisherService events;
    @Mock private EntityManager em;

    @InjectMocks private PlayerService service;

    private final AtomicLong idSeq = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "em", em);
        org.mockito.Mockito.lenient().when(playerRepo.save(any(Player.class)))
                .thenAnswer(inv -> {
                    Player p = inv.getArgument(0);
                    if (p.getId() == null) p.setId(idSeq.incrementAndGet());
                    return p;
                });
        org.mockito.Mockito.lenient().when(titleRepo.findByPlayerIdAndIsCurrentTrue(any()))
                .thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("getProfile / getProfileByEmail / getProfileBySupabaseId")
    class GetProfile {

        @Test
        @DisplayName("getProfile_notFound_throws404")
        void getProfile_notFound_throws404() {
            when(playerRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getProfile(1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("getProfile_existing_returnsResponse")
        void getProfile_existing_returnsResponse() {
            Player p = Player.builder().firstName("A").lastName("B").build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            PlayerProfileResponse r = service.getProfile(1L);
            assertThat(r.id()).isEqualTo(1L);
            assertThat(r.firstName()).isEqualTo("A");
        }

        @Test
        @DisplayName("getProfileByEmail_blankEmail_throws400")
        void getProfileByEmail_blankEmail_throws400() {
            assertThatThrownBy(() -> service.getProfileByEmail("   "))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("getProfileByEmail_notFound_throws404")
        void getProfileByEmail_notFound_throws404() {
            when(playerRepo.findByEmail("a@b.cl")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getProfileByEmail("a@b.cl"))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("getProfileByEmail_existing_returnsResponse")
        void getProfileByEmail_existing_returnsResponse() {
            Player p = Player.builder().firstName("A").lastName("B").email("a@b.cl").build();
            p.setId(1L);
            when(playerRepo.findByEmail("a@b.cl")).thenReturn(Optional.of(p));
            PlayerProfileResponse r = service.getProfileByEmail("a@b.cl");
            assertThat(r.email()).isEqualTo("a@b.cl");
        }

        @Test
        @DisplayName("getProfileBySupabaseId_notFound_throws404")
        void getProfileBySupabaseId_notFound_throws404() {
            UUID uid = UUID.randomUUID();
            when(playerRepo.findBySupabaseUserId(uid)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getProfileBySupabaseId(uid))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("getProfileBySupabaseId_existing_returnsResponse")
        void getProfileBySupabaseId_existing_returnsResponse() {
            UUID uid = UUID.randomUUID();
            Player p = Player.builder().firstName("A").lastName("B").build();
            p.setId(1L);
            when(playerRepo.findBySupabaseUserId(uid)).thenReturn(Optional.of(p));
            assertThat(service.getProfileBySupabaseId(uid).id()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("search_blankQuery_throws400")
        void search_blankQuery_throws400() {
            assertThatThrownBy(() -> service.search("  ", 20))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("search_capsLimitAt50")
        void search_capsLimitAt50() {
            when(playerRepo.searchFuzzy(eq("ana"), eq(50))).thenReturn(List.of());
            service.search("ana", 9999);
            verify(playerRepo).searchFuzzy(eq("ana"), eq(50));
        }

        @Test
        @DisplayName("search_mapsResultsWithTitleIfPresent")
        void search_mapsResultsWithTitleIfPresent() {
            Player p = Player.builder().firstName("A").lastName("B").build();
            p.setId(1L);
            when(playerRepo.searchFuzzy(eq("a"), anyInt())).thenReturn(List.of(p));
            List<PlayerSearchResponse> r = service.search("a", 10);
            assertThat(r).hasSize(1);
        }

        private int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }
    }

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("updateProfile_notFound_throws404")
        void updateProfile_notFound_throws404() {
            when(playerRepo.findById(1L)).thenReturn(Optional.empty());
            UpdateProfileRequest req = new UpdateProfileRequest("A", "B", null, null);
            assertThatThrownBy(() -> service.updateProfile(1L, req))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("updateProfile_validFields_savesAndPublishesUpdated")
        void updateProfile_validFields_savesAndPublishesUpdated() {
            Player p = Player.builder().firstName("Old").lastName("Last").build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            service.updateProfile(1L, new UpdateProfileRequest("New", "Name", null, null));
            assertThat(p.getFirstName()).isEqualTo("New");
            verify(events).publishUserUpdated(eq(1L), any());
        }

        @Test
        @DisplayName("updateProfile_clubNotFound_throws404")
        void updateProfile_clubNotFound_throws404() {
            Player p = Player.builder().firstName("A").build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            when(clubRepo.findById(99)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.updateProfile(1L,
                    new UpdateProfileRequest(null, null, 99, null)))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("updateProfile_setsClubAndRegion")
        void updateProfile_setsClubAndRegion() {
            Player p = Player.builder().firstName("A").build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            Club c = Club.builder().id(7).name("Club").build();
            when(clubRepo.findById(7)).thenReturn(Optional.of(c));
            service.updateProfile(1L, new UpdateProfileRequest(null, null, 7, "Region"));
            assertThat(p.getClub()).isSameAs(c);
            assertThat(p.getRegion()).isEqualTo("Region");
        }

        @Test
        @DisplayName("updateProfile_noChanges_doesNotPublish")
        void updateProfile_noChanges_doesNotPublish() {
            Player p = Player.builder().firstName("A").build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            service.updateProfile(1L, new UpdateProfileRequest(null, null, null, null));
            verify(events, never()).publishUserUpdated(any(), any());
        }
    }

    @Nested
    @DisplayName("updateElo")
    class UpdateElo {

        @Test
        @DisplayName("updateElo_nationalRating_persistsAndPublishesEvent")
        void updateElo_nationalRating_persistsAndPublishesEvent() {
            Player p = Player.builder().eloNational(1500).build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            service.updateElo(1L, new UpdateEloRequest(RatingType.NATIONAL, 1516, "ETL"));
            assertThat(p.getEloNational()).isEqualTo(1516);
            verify(events).publishEloUpdated(eq(1L), eq(1500), eq(1516), eq(RatingType.NATIONAL), eq(null));
            verify(historyRepo).save(any(RatingHistory.class));
        }

        @Test
        @DisplayName("updateElo_zeroOldElo_skipsDelta")
        void updateElo_zeroOldElo_skipsDelta() {
            Player p = Player.builder().build(); // eloNational = null
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            service.updateElo(1L, new UpdateEloRequest(RatingType.NATIONAL, 1500, "ETL"));
            verify(historyRepo).save(any(RatingHistory.class));
        }

        @Test
        @DisplayName("updateElo_fideRapidRating_updatesCorrectField")
        void updateElo_fideRapidRating_updatesCorrectField() {
            Player p = Player.builder().eloFideRapid(1400).build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            service.updateElo(1L, new UpdateEloRequest(RatingType.FIDE_RAPID, 1450, null));
            assertThat(p.getEloFideRapid()).isEqualTo(1450);
        }
    }

    @Nested
    @DisplayName("getRatingHistory")
    class RatingHistoryQ {

        @Test
        @DisplayName("getRatingHistory_playerNotFound_throws404")
        void getRatingHistory_playerNotFound_throws404() {
            when(playerRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRatingHistory(1L, RatingType.NATIONAL))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("getRatingHistory_mapsEntries")
        void getRatingHistory_mapsEntries() {
            Player p = Player.builder().build();
            p.setId(1L);
            when(playerRepo.findById(1L)).thenReturn(Optional.of(p));
            RatingHistory rh = RatingHistory.builder().id(1L).player(p)
                    .ratingType(RatingType.NATIONAL).ratingValue(1500).build();
            when(historyRepo.findByPlayerIdAndRatingTypeOrderByRecordedAtDesc(1L, RatingType.NATIONAL))
                    .thenReturn(List.of(rh));
            List<RatingHistoryResponse> r = service.getRatingHistory(1L, RatingType.NATIONAL);
            assertThat(r).hasSize(1);
            assertThat(r.get(0).ratingValue()).isEqualTo(1500);
        }
    }

    @Nested
    @DisplayName("syncFromAuth")
    class Sync {

        @Test
        @DisplayName("syncFromAuth_existingPlayer_updatesFields")
        void syncFromAuth_existingPlayer_updatesFields() {
            Player existing = Player.builder().firstName("Old").lastName("Last").build();
            existing.setId(5L);
            when(playerRepo.findById(5L)).thenReturn(Optional.of(existing));
            AuthSyncRequest req = new AuthSyncRequest(5L, "a@b.cl", "New", "Last", "lichu");
            PlayerProfileResponse r = service.syncFromAuth(req);
            assertThat(r.firstName()).isEqualTo("New");
            assertThat(existing.getEmail()).isEqualTo("a@b.cl");
            assertThat(existing.getLichessUsername()).isEqualTo("lichu");
        }

        @Test
        @DisplayName("syncFromAuth_existingPlayerHasEmail_doesNotOverwrite")
        void syncFromAuth_existingPlayerHasEmail_doesNotOverwrite() {
            Player existing = Player.builder().firstName("A").lastName("B").email("old@x.cl").build();
            existing.setId(5L);
            when(playerRepo.findById(5L)).thenReturn(Optional.of(existing));
            AuthSyncRequest req = new AuthSyncRequest(5L, "new@x.cl", null, null, null);
            service.syncFromAuth(req);
            assertThat(existing.getEmail()).isEqualTo("old@x.cl");
        }

        @Test
        @DisplayName("syncFromAuth_claimFederatedByName_reassignsId")
        void syncFromAuth_claimFederatedByName_reassignsId() {
            // Sin existing player id, hay federado con nombre coincidente.
            Player federated = Player.builder().firstName("Magnus").lastName("Carlsen").build();
            federated.setId(999L);
            when(playerRepo.findById(5L)).thenReturn(Optional.empty()).thenReturn(Optional.of(federated));
            when(playerRepo.findFederatedByFullName("Magnus", "Carlsen"))
                    .thenReturn(Optional.of(federated));
            // El UPDATE native devuelve 1 fila afectada
            jakarta.persistence.Query q = org.mockito.Mockito.mock(jakarta.persistence.Query.class);
            when(em.createNativeQuery(org.mockito.ArgumentMatchers.contains("UPDATE player SET id"))).thenReturn(q);
            when(q.setParameter(any(String.class), any())).thenReturn(q);
            when(q.executeUpdate()).thenReturn(1);

            AuthSyncRequest req = new AuthSyncRequest(5L, "m@c.cl", "Magnus", "Carlsen", null);
            service.syncFromAuth(req);
        }
    }

    @Nested
    @DisplayName("provisionBySupabaseId")
    class Provision {

        @Test
        @DisplayName("provision_alreadyExistsBySupabaseId_returnsExisting")
        void provision_alreadyExistsBySupabaseId_returnsExisting() {
            UUID uid = UUID.randomUUID();
            Player p = Player.builder().firstName("A").lastName("B").build();
            p.setId(1L);
            when(playerRepo.findBySupabaseUserId(uid)).thenReturn(Optional.of(p));
            ProvisionPlayerRequest req = new ProvisionPlayerRequest(uid, null, null, null, null, null);
            PlayerProfileResponse r = service.provisionBySupabaseId(req);
            assertThat(r.id()).isEqualTo(1L);
            verify(playerRepo, never()).save(any());
        }

        @Test
        @DisplayName("provision_matchByEmail_associatesAndReturns")
        void provision_matchByEmail_associatesAndReturns() {
            UUID uid = UUID.randomUUID();
            Player byEmail = Player.builder().firstName("A").lastName("B").email("a@b.cl").build();
            byEmail.setId(7L);
            when(playerRepo.findBySupabaseUserId(uid)).thenReturn(Optional.empty());
            when(playerRepo.findByEmail("a@b.cl")).thenReturn(Optional.of(byEmail));
            ProvisionPlayerRequest req = new ProvisionPlayerRequest(uid, "a@b.cl", null, null, null, null);
            PlayerProfileResponse r = service.provisionBySupabaseId(req);
            assertThat(byEmail.getSupabaseUserId()).isEqualTo(uid);
            assertThat(r.id()).isEqualTo(7L);
        }

        @Test
        @DisplayName("provision_newPlayer_createsAndSaves")
        void provision_newPlayer_createsAndSaves() {
            UUID uid = UUID.randomUUID();
            when(playerRepo.findBySupabaseUserId(uid)).thenReturn(Optional.empty());
            org.mockito.Mockito.lenient().when(playerRepo.findByEmail(any())).thenReturn(Optional.empty());
            when(playerRepo.saveAndFlush(any(Player.class))).thenAnswer(inv -> {
                Player p = inv.getArgument(0);
                if (p.getId() == null) p.setId(99L);
                return p;
            });
            ProvisionPlayerRequest req = new ProvisionPlayerRequest(uid, "x@y.cl", "F", "L", "lich", null);
            PlayerProfileResponse r = service.provisionBySupabaseId(req);
            assertThat(r.id()).isEqualTo(99L);
        }

        @Test
        @DisplayName("provision_concurrentDuplicate_reLeesWinner")
        void provision_concurrentDuplicate_reLeesWinner() {
            UUID uid = UUID.randomUUID();
            Player winner = Player.builder().firstName("W").lastName("N").build();
            winner.setId(123L);
            when(playerRepo.findBySupabaseUserId(uid))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            org.mockito.Mockito.lenient().when(playerRepo.findByEmail(any())).thenReturn(Optional.empty());
            org.mockito.Mockito.doThrow(new DataIntegrityViolationException("dup"))
                    .when(playerRepo).saveAndFlush(any(Player.class));
            ProvisionPlayerRequest req = new ProvisionPlayerRequest(uid, null, "F", "L", null, null);
            PlayerProfileResponse r = service.provisionBySupabaseId(req);
            assertThat(r.id()).isEqualTo(123L);
        }
    }
}
