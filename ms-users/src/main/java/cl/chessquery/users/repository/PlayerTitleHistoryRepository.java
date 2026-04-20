package cl.chessquery.users.repository;

import cl.chessquery.users.entity.PlayerTitleHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerTitleHistoryRepository extends JpaRepository<PlayerTitleHistory, Long> {

    Optional<PlayerTitleHistory> findByPlayerIdAndIsCurrentTrue(Long playerId);

    List<PlayerTitleHistory> findByPlayerIdOrderByTitleDateDesc(Long playerId);
}
