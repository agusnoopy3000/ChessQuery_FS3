package cl.chessquery.tournament.repository;

import cl.chessquery.tournament.entity.Tournament;
import cl.chessquery.tournament.entity.TournamentFormat;
import cl.chessquery.tournament.entity.TournamentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Page<Tournament> findByStatusAndFormat(TournamentStatus status, TournamentFormat format, Pageable pageable);

    Page<Tournament> findByStatus(TournamentStatus status, Pageable pageable);

    Page<Tournament> findByFormat(TournamentFormat format, Pageable pageable);
}
