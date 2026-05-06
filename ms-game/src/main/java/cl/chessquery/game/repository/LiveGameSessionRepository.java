package cl.chessquery.game.repository;

import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveGameSessionRepository extends JpaRepository<LiveGameSession, Long> {
    List<LiveGameSession> findByStatus(SessionStatus status);
    List<LiveGameSession> findByWhitePlayerIdOrBlackPlayerIdAndStatusIn(
            Long whiteId, Long blackId, List<SessionStatus> statuses);
}
