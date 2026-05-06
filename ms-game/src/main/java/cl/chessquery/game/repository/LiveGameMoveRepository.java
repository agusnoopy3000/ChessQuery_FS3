package cl.chessquery.game.repository;

import cl.chessquery.game.entity.LiveGameMove;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveGameMoveRepository extends JpaRepository<LiveGameMove, Long> {
    List<LiveGameMove> findBySessionIdOrderByMoveNumberAscColorAsc(Long sessionId);
    long countBySessionId(Long sessionId);
}
