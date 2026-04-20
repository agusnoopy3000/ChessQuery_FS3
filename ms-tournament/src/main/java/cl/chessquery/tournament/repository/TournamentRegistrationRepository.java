package cl.chessquery.tournament.repository;

import cl.chessquery.tournament.entity.RegistrationStatus;
import cl.chessquery.tournament.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentRegistrationRepository extends JpaRepository<TournamentRegistration, Long> {

    List<TournamentRegistration> findByTournamentId(Long tournamentId);

    Optional<TournamentRegistration> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    long countByTournamentIdAndStatus(Long tournamentId, RegistrationStatus status);
}
