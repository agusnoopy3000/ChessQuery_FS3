package cl.chessquery.tournament.messaging;

import cl.chessquery.tournament.entity.TournamentPairing;
import cl.chessquery.tournament.repository.TournamentPairingRepository;
import cl.chessquery.tournament.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameFinishedConsumerTest {

    @Mock private TournamentService tournamentService;
    @Mock private TournamentPairingRepository pairingRepo;
    @InjectMocks private GameFinishedConsumer consumer;

    private ChessEvent event(Map<String, Object> payload) {
        ChessEvent e = new ChessEvent();
        e.setEventId("evt-1");
        e.setEventType("game.finished");
        e.setPayload(payload);
        return e;
    }

    @Test
    void registraResultadoCuandoVieneDePartidaDeTorneo() {
        TournamentPairing p = new TournamentPairing();
        p.setId(5L);
        when(pairingRepo.findById(5L)).thenReturn(Optional.of(p));

        consumer.onGameFinished(event(Map.of("tournamentPairingId", 5, "result", "1-0", "gameId", 99)));

        verify(tournamentService).recordResult(5L, "1-0");
    }

    @Test
    void ignoraEventoSinTournamentPairingId() {
        // Caso típico: el eco del propio game.finished de ms-tournament (usa pairingId, no tournamentPairingId)
        consumer.onGameFinished(event(Map.of("pairingId", 5, "result", "1-0")));

        verifyNoInteractions(tournamentService);
        verifyNoInteractions(pairingRepo);
    }

    @Test
    void ignoraSiElPairingYaTieneResultado() {
        TournamentPairing p = new TournamentPairing();
        p.setId(5L);
        p.setResult("0-1");
        when(pairingRepo.findById(5L)).thenReturn(Optional.of(p));

        consumer.onGameFinished(event(Map.of("tournamentPairingId", 5, "result", "1-0")));

        verify(tournamentService, never()).recordResult(anyLong(), anyString());
    }
}
