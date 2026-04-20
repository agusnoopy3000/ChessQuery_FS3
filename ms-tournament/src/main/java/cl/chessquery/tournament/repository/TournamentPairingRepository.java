package cl.chessquery.tournament.repository;

import cl.chessquery.tournament.entity.TournamentPairing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentPairingRepository extends JpaRepository<TournamentPairing, Long> {

    List<TournamentPairing> findByRoundId(Long roundId);

    List<TournamentPairing> findByRoundIdIn(List<Long> roundIds);
}
