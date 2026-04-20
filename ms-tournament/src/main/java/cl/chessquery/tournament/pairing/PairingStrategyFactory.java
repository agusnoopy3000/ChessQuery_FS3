package cl.chessquery.tournament.pairing;

import cl.chessquery.tournament.entity.TournamentFormat;
import org.springframework.stereotype.Component;

@Component
public class PairingStrategyFactory {

    public PairingStrategy getStrategy(TournamentFormat format) {
        return switch (format) {
            case SWISS       -> new SwissPairingStrategy();
            case ROUND_ROBIN -> new RoundRobinPairingStrategy();
            case KNOCKOUT    -> new KnockoutPairingStrategy();
        };
    }
}
