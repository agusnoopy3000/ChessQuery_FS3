package cl.chessquery.game.repository;

import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiveGameSessionRepository extends JpaRepository<LiveGameSession, Long> {
    List<LiveGameSession> findByStatus(SessionStatus status);
    List<LiveGameSession> findByWhitePlayerIdOrBlackPlayerIdAndStatusIn(
            Long whiteId, Long blackId, List<SessionStatus> statuses);

    /**
     * Bloqueo pesimista por fila para serializar moves concurrentes sobre la
     * misma sesion. Sin esto, dos requests simultaneas (doble-click del
     * cliente o retry del axios overlap con el original) leen el mismo
     * currentFen, insertan moves con el mismo (session_id, move_number) y la
     * segunda revienta con 'duplicate key value violates uq_session_move'.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM LiveGameSession s WHERE s.id = :id")
    Optional<LiveGameSession> findByIdForUpdate(@Param("id") Long id);
}
