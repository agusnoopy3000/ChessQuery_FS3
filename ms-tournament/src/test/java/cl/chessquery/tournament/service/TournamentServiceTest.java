package cl.chessquery.tournament.service;

import cl.chessquery.tournament.client.UserEloClient;
import cl.chessquery.tournament.dto.CreateTournamentRequest;
import cl.chessquery.tournament.dto.RegistrationResponse;
import cl.chessquery.tournament.dto.TournamentResponse;
import cl.chessquery.tournament.entity.RegistrationStatus;
import cl.chessquery.tournament.entity.Tournament;
import cl.chessquery.tournament.entity.TournamentFormat;
import cl.chessquery.tournament.entity.TournamentRegistration;
import cl.chessquery.tournament.entity.TournamentStatus;
import cl.chessquery.tournament.pairing.PairingStrategyFactory;
import cl.chessquery.tournament.repository.TournamentPairingRepository;
import cl.chessquery.tournament.repository.TournamentRegistrationRepository;
import cl.chessquery.tournament.repository.TournamentRepository;
import cl.chessquery.tournament.repository.TournamentRoundRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock private TournamentRepository tournamentRepo;
    @Mock private TournamentRegistrationRepository registrationRepo;
    @Mock private TournamentRoundRepository roundRepo;
    @Mock private TournamentPairingRepository pairingRepo;
    @Mock private PairingStrategyFactory strategyFactory;
    @Mock private UserEloClient userEloClient;
    @Mock private EventPublisherService events;

    @InjectMocks
    private TournamentService tournamentService;

    @Test
    void createTournament_savesAndPublishesEvent() {
        CreateTournamentRequest req = new CreateTournamentRequest(
                "Test Tourney", "Desc", TournamentFormat.SWISS, LocalDate.now(), null, "Online", 100, 5, 99L, 1000, 2000, "10+0", true
        );
        
        when(tournamentRepo.save(any(Tournament.class))).thenAnswer(inv -> {
            Tournament t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });

        TournamentResponse res = tournamentService.createTournament(req, 99L);

        assertThat(res.name()).isEqualTo("Test Tourney");
        assertThat(res.format()).isEqualTo("SWISS");
        
        verify(tournamentRepo, times(1)).save(any(Tournament.class));
        verify(events, times(1)).publishTournamentCreated(1L, "Test Tourney", 99L, "SWISS");
    }

    @Test
    void joinTournament_whenOpen_createsRegistration() {
        Tournament t = Tournament.builder()
                .id(10L)
                .name("Torneo Open")
                .status(TournamentStatus.OPEN)
                .requiresApproval(true)
                .build();

        when(tournamentRepo.findById(10L)).thenReturn(Optional.of(t));
        when(registrationRepo.findByTournamentIdAndPlayerId(10L, 55L)).thenReturn(Optional.empty());
        when(userEloClient.getElo(55L)).thenReturn(1500);

        when(registrationRepo.save(any(TournamentRegistration.class))).thenAnswer(inv -> {
            TournamentRegistration reg = inv.getArgument(0);
            reg.setId(100L);
            return reg;
        });

        RegistrationResponse res = tournamentService.joinTournament(10L, 55L);

        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.playerId()).isEqualTo(55L);

        verify(registrationRepo, times(1)).save(any(TournamentRegistration.class));
        verify(events, times(1)).publishRegistrationPending(10L, 55L, null, "Torneo Open");
    }
}
