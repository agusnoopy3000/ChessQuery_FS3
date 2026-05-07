package cl.chessquery.game.repository;

import cl.chessquery.game.entity.LiveGameMove;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveGameMoveRepository extends JpaRepository<LiveGameMove, Long> {
    /**
     * Ordena por timestamp de inserción para garantizar el orden de juego
     * w1, b1, w2, b2... — usar moveNumber+color rompe porque 'b' < 'w'
     * alfabéticamente y arruinaría la secuencia del PGN.
     */
    List<LiveGameMove> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    long countBySessionId(Long sessionId);
}
