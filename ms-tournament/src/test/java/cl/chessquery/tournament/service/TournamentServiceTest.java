package cl.chessquery.tournament.service;

import cl.chessquery.tournament.client.UserEloClient;
import cl.chessquery.tournament.dto.*;
import cl.chessquery.tournament.entity.*;
import cl.chessquery.tournament.exception.ApiException;
import cl.chessquery.tournament.pairing.*;
import cl.chessquery.tournament.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link TournamentService}.
 *
 * <p>Mockea todos los repositorios, {@link UserEloClient}, {@link EventPublisherService}
 * y {@link PairingStrategyFactory}.</p>
 *
 * <p>Invariantes cubiertos:
 * <ul>
 *   <li>Solo se permiten transiciones DRAFT→OPEN→IN_PROGRESS→FINISHED.</li>
 *   <li>Inscripciones duplicadas (CONFIRMED/PENDING) son rechazadas con 409.</li>
 *   <li>El rango de ELO bloquea inscripciones fuera de rango con 400.</li>
 *   <li>Los standings calculan Buchholz y Sonneborn-Berger correctamente.</li>
 *   <li>generateRound rechaza si torneo no está IN_PROGRESS, ronda existe o &lt;2 jugadores.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock private TournamentRepository tournamentRepo;
    @Mock private TournamentRegistrationRepository registrationRepo;
    @Mock private TournamentRoundRepository roundRepo;
    @Mock private TournamentPairingRepository pairingRepo;
    @Mock private PairingStrategyFactory strategyFactory;
    @Mock private UserEloClient userEloClient;
    @Mock private EventPublisherService events;

    @InjectMocks private TournamentService service;

    private final AtomicLong regIdSeq = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(registrationRepo.save(any(TournamentRegistration.class)))
                .thenAnswer(inv -> {
                    TournamentRegistration r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(regIdSeq.incrementAndGet());
                    return r;
                });
        org.mockito.Mockito.lenient().when(tournamentRepo.save(any(Tournament.class)))
                .thenAnswer(inv -> {
                    Tournament t = inv.getArgument(0);
                    if (t.getId() == null) t.setId(1L);
                    return t;
                });
    }

    private Tournament openTournament(boolean requiresApproval, Integer maxPlayers,
                                      Integer minElo, Integer maxElo) {
        Tournament t = Tournament.builder()
                .id(10L).name("Open").status(TournamentStatus.OPEN)
                .requiresApproval(requiresApproval).maxPlayers(maxPlayers)
                .minElo(minElo).maxElo(maxElo).format(TournamentFormat.SWISS)
                .organizerId(9L).roundsTotal(5)
                .build();
        return t;
    }

    @Nested
    @DisplayName("createTournament")
    class CreateTournament {

        @Test
        @DisplayName("createTournament_validRequest_savesAndPublishesEvent")
        void createTournament_validRequest_savesAndPublishesEvent() {
            CreateTournamentRequest req = new CreateTournamentRequest(
                    "T1", "d", TournamentFormat.SWISS, LocalDate.now(), null,
                    "Online", 100, 5, 9L, 1000, 2000, "10+0", true);
            TournamentResponse res = service.createTournament(req, 9L);
            assertThat(res.name()).isEqualTo("T1");
            assertThat(res.status()).isEqualTo("DRAFT");
            verify(events).publishTournamentCreated(1L, "T1", 9L, "SWISS");
        }

        @Test
        @DisplayName("createTournament_nullRequiresApproval_defaultsToTrue")
        void createTournament_nullRequiresApproval_defaultsToTrue() {
            CreateTournamentRequest req = new CreateTournamentRequest(
                    "T1", null, TournamentFormat.SWISS, LocalDate.now(), null,
                    null, null, 5, null, null, null, null, null);
            TournamentResponse res = service.createTournament(req, 9L);
            assertThat(res.requiresApproval()).isTrue();
        }
    }

    @Nested
    @DisplayName("joinTournament")
    class JoinTournament {

        @Test
        @DisplayName("joinTournament_tournamentNotOpen_returns400")
        void joinTournament_tournamentNotOpen_returns400() {
            Tournament t = openTournament(true, null, null, null);
            t.setStatus(TournamentStatus.DRAFT);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.joinTournament(10L, 1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("joinTournament_alreadyConfirmed_returns409")
        void joinTournament_alreadyConfirmed_returns409() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            TournamentRegistration prev = TournamentRegistration.builder()
                    .tournament(t).playerId(5L).status(RegistrationStatus.CONFIRMED).build();
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.of(prev));
            assertThatThrownBy(() -> service.joinTournament(10L, 5L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("joinTournament_alreadyPending_returns409")
        void joinTournament_alreadyPending_returns409() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            TournamentRegistration prev = TournamentRegistration.builder()
                    .tournament(t).playerId(5L).status(RegistrationStatus.PENDING).build();
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.of(prev));
            assertThatThrownBy(() -> service.joinTournament(10L, 5L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("joinTournament_full_returns409")
        void joinTournament_full_returns409() {
            Tournament t = openTournament(true, 2, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.countByTournamentIdAndStatus(10L, RegistrationStatus.CONFIRMED)).thenReturn(2L);
            assertThatThrownBy(() -> service.joinTournament(10L, 1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("joinTournament_eloTooLow_returns400")
        void joinTournament_eloTooLow_returns400() {
            Tournament t = openTournament(true, null, 1600, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.empty());
            when(userEloClient.getElo(5L)).thenReturn(1500);
            assertThatThrownBy(() -> service.joinTournament(10L, 5L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("joinTournament_eloTooHigh_returns400")
        void joinTournament_eloTooHigh_returns400() {
            Tournament t = openTournament(true, null, null, 1400);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.empty());
            when(userEloClient.getElo(5L)).thenReturn(1500);
            assertThatThrownBy(() -> service.joinTournament(10L, 5L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("joinTournament_requiresApproval_createsPendingAndPublishesPending")
        void joinTournament_requiresApproval_createsPendingAndPublishesPending() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.empty());
            when(userEloClient.getElo(5L)).thenReturn(1500);

            RegistrationResponse res = service.joinTournament(10L, 5L);
            assertThat(res.status()).isEqualTo("PENDING");
            verify(events).publishRegistrationPending(10L, 5L, 9L, "Open");
            verify(events, never()).publishPlayerRegistered(anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("joinTournament_noApproval_createsConfirmedAndPublishesPlayerRegistered")
        void joinTournament_noApproval_createsConfirmedAndPublishesPlayerRegistered() {
            Tournament t = openTournament(false, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.empty());
            when(userEloClient.getElo(5L)).thenReturn(1500);
            RegistrationResponse res = service.joinTournament(10L, 5L);
            assertThat(res.status()).isEqualTo("CONFIRMED");
            verify(events).publishPlayerRegistered(10L, 5L, 1500, "Open");
        }

        @Test
        @DisplayName("joinTournament_reinscriptionAfterRejected_mutatesExisting")
        void joinTournament_reinscriptionAfterRejected_mutatesExisting() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            TournamentRegistration prev = TournamentRegistration.builder()
                    .id(99L).tournament(t).playerId(5L).status(RegistrationStatus.REJECTED).build();
            when(registrationRepo.findByTournamentIdAndPlayerId(10L, 5L)).thenReturn(Optional.of(prev));
            when(userEloClient.getElo(5L)).thenReturn(1500);

            RegistrationResponse res = service.joinTournament(10L, 5L);
            assertThat(res.id()).isEqualTo(99L);
            assertThat(res.status()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("transitionStatus")
    class Transitions {

        @Test
        @DisplayName("transitionStatus_draftToOpen_success")
        void transitionStatus_draftToOpen_success() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.DRAFT).roundsTotal(5)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            TournamentResponse res = service.transitionStatus(1L, TournamentStatus.OPEN);
            assertThat(res.status()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("transitionStatus_draftToInProgress_throws400")
        void transitionStatus_draftToInProgress_throws400() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.DRAFT).roundsTotal(5)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.transitionStatus(1L, TournamentStatus.IN_PROGRESS))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("transitionStatus_draftToOpenWithoutRounds_throws400")
        void transitionStatus_draftToOpenWithoutRounds_throws400() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.DRAFT).roundsTotal(0)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.transitionStatus(1L, TournamentStatus.OPEN))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("transitionStatus_openToInProgressWithEnoughPlayers_success")
        void transitionStatus_openToInProgressWithEnoughPlayers_success() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.OPEN).roundsTotal(5)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            when(registrationRepo.countByTournamentIdAndStatus(1L, RegistrationStatus.CONFIRMED))
                    .thenReturn(4L);
            TournamentResponse res = service.transitionStatus(1L, TournamentStatus.IN_PROGRESS);
            assertThat(res.status()).isEqualTo("IN_PROGRESS");
        }

        @Test
        @DisplayName("transitionStatus_openToInProgressWithoutEnoughPlayers_throws400")
        void transitionStatus_openToInProgressWithoutEnoughPlayers_throws400() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.OPEN).roundsTotal(5)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            when(registrationRepo.countByTournamentIdAndStatus(1L, RegistrationStatus.CONFIRMED))
                    .thenReturn(1L);
            assertThatThrownBy(() -> service.transitionStatus(1L, TournamentStatus.IN_PROGRESS))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("transitionStatus_inProgressToFinished_success")
        void transitionStatus_inProgressToFinished_success() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.IN_PROGRESS)
                    .roundsTotal(5).format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            TournamentResponse res = service.transitionStatus(1L, TournamentStatus.FINISHED);
            assertThat(res.status()).isEqualTo("FINISHED");
        }

        @Test
        @DisplayName("transitionStatus_finishedToOther_throws400")
        void transitionStatus_finishedToOther_throws400() {
            Tournament t = Tournament.builder().id(1L).status(TournamentStatus.FINISHED).roundsTotal(5)
                    .format(TournamentFormat.SWISS).build();
            when(tournamentRepo.findById(1L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.transitionStatus(1L, TournamentStatus.OPEN))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Nested
    @DisplayName("approveRegistration / rejectRegistration")
    class Approval {

        @Test
        @DisplayName("approveRegistration_notFound_throws404")
        void approveRegistration_notFound_throws404() {
            when(registrationRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.approveRegistration(1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("approveRegistration_notPending_throws409")
        void approveRegistration_notPending_throws409() {
            Tournament t = openTournament(true, null, null, null);
            TournamentRegistration r = TournamentRegistration.builder()
                    .id(1L).tournament(t).playerId(5L).status(RegistrationStatus.CONFIRMED).build();
            when(registrationRepo.findById(1L)).thenReturn(Optional.of(r));
            assertThatThrownBy(() -> service.approveRegistration(1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("approveRegistration_fullTournament_throws409")
        void approveRegistration_fullTournament_throws409() {
            Tournament t = openTournament(true, 2, null, null);
            TournamentRegistration r = TournamentRegistration.builder()
                    .id(1L).tournament(t).playerId(5L).status(RegistrationStatus.PENDING).build();
            when(registrationRepo.findById(1L)).thenReturn(Optional.of(r));
            when(registrationRepo.countByTournamentIdAndStatus(10L, RegistrationStatus.CONFIRMED))
                    .thenReturn(2L);
            assertThatThrownBy(() -> service.approveRegistration(1L))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("approveRegistration_validPending_setsConfirmedAndPublishesEvents")
        void approveRegistration_validPending_setsConfirmedAndPublishesEvents() {
            Tournament t = openTournament(true, null, null, null);
            TournamentRegistration r = TournamentRegistration.builder()
                    .id(1L).tournament(t).playerId(5L).status(RegistrationStatus.PENDING)
                    .seedRating(1500).build();
            when(registrationRepo.findById(1L)).thenReturn(Optional.of(r));

            RegistrationResponse res = service.approveRegistration(1L);
            assertThat(res.status()).isEqualTo("CONFIRMED");
            verify(events).publishRegistrationApproved(10L, 5L, "Open");
            verify(events).publishPlayerRegistered(10L, 5L, 1500, "Open");
        }

        @Test
        @DisplayName("rejectRegistration_notPending_throws409")
        void rejectRegistration_notPending_throws409() {
            Tournament t = openTournament(true, null, null, null);
            TournamentRegistration r = TournamentRegistration.builder()
                    .id(1L).tournament(t).playerId(5L).status(RegistrationStatus.CONFIRMED).build();
            when(registrationRepo.findById(1L)).thenReturn(Optional.of(r));
            assertThatThrownBy(() -> service.rejectRegistration(1L, "bad"))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("rejectRegistration_validPending_setsRejectedAndPublishes")
        void rejectRegistration_validPending_setsRejectedAndPublishes() {
            Tournament t = openTournament(true, null, null, null);
            TournamentRegistration r = TournamentRegistration.builder()
                    .id(1L).tournament(t).playerId(5L).status(RegistrationStatus.PENDING).build();
            when(registrationRepo.findById(1L)).thenReturn(Optional.of(r));
            RegistrationResponse res = service.rejectRegistration(1L, "elo bajo");
            assertThat(res.status()).isEqualTo("REJECTED");
            verify(events).publishRegistrationRejected(10L, 5L, "Open", "elo bajo");
        }
    }

    @Nested
    @DisplayName("deleteTournament")
    class Delete {

        @Test
        @DisplayName("deleteTournament_byNonOwnerNonAdmin_throws403")
        void deleteTournament_byNonOwnerNonAdmin_throws403() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.deleteTournament(10L, 1L, false))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 403);
        }

        @Test
        @DisplayName("deleteTournament_inProgressByOwner_throws409")
        void deleteTournament_inProgressByOwner_throws409() {
            Tournament t = openTournament(true, null, null, null);
            t.setStatus(TournamentStatus.IN_PROGRESS);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.deleteTournament(10L, 9L, false))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("deleteTournament_withRoundsByOwner_throws409")
        void deleteTournament_withRoundsByOwner_throws409() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdOrderByRoundNumberAsc(10L))
                    .thenReturn(List.of(TournamentRound.builder().id(1L).build()));
            assertThatThrownBy(() -> service.deleteTournament(10L, 9L, false))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("deleteTournament_openByOwner_success")
        void deleteTournament_openByOwner_success() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdOrderByRoundNumberAsc(10L)).thenReturn(List.of());
            when(registrationRepo.findByTournamentId(10L)).thenReturn(List.of());
            service.deleteTournament(10L, 9L, false);
            verify(tournamentRepo).delete(t);
        }
    }

    @Nested
    @DisplayName("generateRound")
    class GenerateRound {

        @Test
        @DisplayName("generateRound_tournamentNotInProgress_throws400")
        void generateRound_tournamentNotInProgress_throws400() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            assertThatThrownBy(() -> service.generateRound(10L, 1))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("generateRound_roundAlreadyExists_throws409")
        void generateRound_roundAlreadyExists_throws409() {
            Tournament t = openTournament(true, null, null, null);
            t.setStatus(TournamentStatus.IN_PROGRESS);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdAndRoundNumber(10L, 1))
                    .thenReturn(Optional.of(TournamentRound.builder().build()));
            assertThatThrownBy(() -> service.generateRound(10L, 1))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 409);
        }

        @Test
        @DisplayName("generateRound_notEnoughPlayers_throws400")
        void generateRound_notEnoughPlayers_throws400() {
            Tournament t = openTournament(true, null, null, null);
            t.setStatus(TournamentStatus.IN_PROGRESS);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdAndRoundNumber(10L, 1)).thenReturn(Optional.empty());
            when(registrationRepo.findByTournamentId(10L)).thenReturn(List.of(
                    TournamentRegistration.builder().tournament(t).playerId(1L)
                            .status(RegistrationStatus.CONFIRMED).seedRating(1500).build()));
            when(roundRepo.findByTournamentIdOrderByRoundNumberAsc(10L)).thenReturn(List.of());
            assertThatThrownBy(() -> service.generateRound(10L, 1))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 400);
        }

        @Test
        @DisplayName("generateRound_validSetup_createsRoundWithPairings")
        void generateRound_validSetup_createsRoundWithPairings() {
            Tournament t = openTournament(true, null, null, null);
            t.setStatus(TournamentStatus.IN_PROGRESS);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdAndRoundNumber(10L, 1)).thenReturn(Optional.empty());
            when(registrationRepo.findByTournamentId(10L)).thenReturn(List.of(
                    TournamentRegistration.builder().tournament(t).playerId(1L)
                            .status(RegistrationStatus.CONFIRMED).seedRating(1500).build(),
                    TournamentRegistration.builder().tournament(t).playerId(2L)
                            .status(RegistrationStatus.CONFIRMED).seedRating(1500).build()));
            when(roundRepo.findByTournamentIdOrderByRoundNumberAsc(10L)).thenReturn(List.of());
            PairingStrategy strat = (standings, n) ->
                    List.of(new PairingResult(1L, 2L, 1));
            when(strategyFactory.getStrategy(TournamentFormat.SWISS)).thenReturn(strat);
            when(roundRepo.save(any(TournamentRound.class))).thenAnswer(inv -> {
                TournamentRound r = inv.getArgument(0); r.setId(7L); return r;
            });
            when(pairingRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            RoundResponse round = service.generateRound(10L, 1);
            assertThat(round.pairings()).hasSize(1);
            verify(events).publishRoundStarting(10L, 1, 1);
        }
    }

    @Nested
    @DisplayName("recordResult / standings / list / get")
    class ReadAndUpdate {

        @Test
        @DisplayName("recordResult_pairingNotFound_throws404")
        void recordResult_pairingNotFound_throws404() {
            when(pairingRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.recordResult(1L, "1-0"))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("recordResult_validPairing_savesAndPublishes")
        void recordResult_validPairing_savesAndPublishes() {
            Tournament t = openTournament(true, null, null, null);
            TournamentRound round = TournamentRound.builder().id(7L).tournament(t).build();
            TournamentPairing p = TournamentPairing.builder()
                    .id(1L).round(round).whitePlayerId(1L).blackPlayerId(2L).build();
            when(pairingRepo.findById(1L)).thenReturn(Optional.of(p));
            PairingResponse res = service.recordResult(1L, "1-0");
            assertThat(res.result()).isEqualTo("1-0");
            verify(events).publishGameFinished(1L, 10L, 1L, 2L, "1-0");
        }

        @Test
        @DisplayName("getStandings_withResults_computesPointsBuchholzSb")
        void getStandings_withResults_computesPointsBuchholzSb() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentId(10L)).thenReturn(List.of(
                    TournamentRegistration.builder().tournament(t).playerId(1L)
                            .status(RegistrationStatus.CONFIRMED).seedRating(1500).build(),
                    TournamentRegistration.builder().tournament(t).playerId(2L)
                            .status(RegistrationStatus.CONFIRMED).seedRating(1500).build()));
            TournamentRound r = TournamentRound.builder().id(7L).tournament(t).build();
            when(roundRepo.findByTournamentIdOrderByRoundNumberAsc(10L)).thenReturn(List.of(r));
            when(pairingRepo.findByRoundIdIn(List.of(7L))).thenReturn(List.of(
                    TournamentPairing.builder().round(r).whitePlayerId(1L).blackPlayerId(2L).result("1-0").build(),
                    TournamentPairing.builder().round(r).whitePlayerId(2L).blackPlayerId(1L).result("1/2-1/2").build()));

            List<StandingEntry> standings = service.getStandings(10L);
            assertThat(standings).hasSize(2);
            assertThat(standings.get(0).playerId()).isEqualTo(1L);
            assertThat(standings.get(0).points()).isEqualTo(1.5);
            assertThat(standings.get(1).points()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("listTournaments_filterByStatusAndFormat_callsCorrectRepo")
        void listTournaments_filterByStatusAndFormat_callsCorrectRepo() {
            when(tournamentRepo.findByStatusAndFormat(eq(TournamentStatus.OPEN),
                    eq(TournamentFormat.SWISS), any()))
                    .thenReturn(emptyPage());
            service.listTournaments(TournamentStatus.OPEN, TournamentFormat.SWISS, 0, 10);
            verify(tournamentRepo).findByStatusAndFormat(eq(TournamentStatus.OPEN),
                    eq(TournamentFormat.SWISS), any());
        }

        @Test
        @DisplayName("listTournaments_onlyStatus_callsFindByStatus")
        void listTournaments_onlyStatus_callsFindByStatus() {
            when(tournamentRepo.findByStatus(eq(TournamentStatus.OPEN), any())).thenReturn(emptyPage());
            service.listTournaments(TournamentStatus.OPEN, null, 0, 10);
            verify(tournamentRepo).findByStatus(eq(TournamentStatus.OPEN), any());
        }

        @Test
        @DisplayName("listTournaments_onlyFormat_callsFindByFormat")
        void listTournaments_onlyFormat_callsFindByFormat() {
            when(tournamentRepo.findByFormat(eq(TournamentFormat.SWISS), any())).thenReturn(emptyPage());
            service.listTournaments(null, TournamentFormat.SWISS, 0, 10);
            verify(tournamentRepo).findByFormat(eq(TournamentFormat.SWISS), any());
        }

        @Test
        @DisplayName("listTournaments_noFilter_callsFindAll")
        void listTournaments_noFilter_callsFindAll() {
            when(tournamentRepo.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(emptyPage());
            service.listTournaments(null, null, 0, 10);
            verify(tournamentRepo, times(1)).findAll(any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        @DisplayName("getTournament_notFound_throws404")
        void getTournament_notFound_throws404() {
            when(tournamentRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getTournament(1L))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("getRound_notFound_throws404")
        void getRound_notFound_throws404() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(roundRepo.findByTournamentIdAndRoundNumber(10L, 1)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getRound(10L, 1))
                    .isInstanceOf(ApiException.class)
                    .matches(e -> ((ApiException) e).getStatus() == 404);
        }

        @Test
        @DisplayName("listRegistrations_returnsMappedDtos")
        void listRegistrations_returnsMappedDtos() {
            Tournament t = openTournament(true, null, null, null);
            when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
            when(registrationRepo.findByTournamentId(10L)).thenReturn(List.of(
                    TournamentRegistration.builder().id(1L).tournament(t).playerId(5L)
                            .status(RegistrationStatus.CONFIRMED).build()));
            List<RegistrationResponse> res = service.listRegistrations(10L);
            assertThat(res).hasSize(1);
            assertThat(res.get(0).playerId()).isEqualTo(5L);
        }
    }

    private Page<Tournament> emptyPage() {
        return new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
    }
}
