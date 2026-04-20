package cl.chessquery.tournament.repository;

import cl.chessquery.tournament.entity.TournamentRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentRoundRepository extends JpaRepository<TournamentRound, Long> {

    Optional<TournamentRound> findByTournamentIdAndRoundNumber(Long tournamentId, int roundNumber);

    List<TournamentRound> findByTournamentIdOrderByRoundNumberAsc(Long tournamentId);
}
