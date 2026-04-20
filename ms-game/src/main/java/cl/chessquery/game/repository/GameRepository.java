package cl.chessquery.game.repository;

import cl.chessquery.game.entity.Game;
import cl.chessquery.game.entity.GameType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Long> {

    Page<Game> findByWhitePlayerIdOrBlackPlayerId(Long playerId1, Long playerId2, Pageable pageable);

    @Query("SELECT g FROM Game g WHERE " +
           "(g.whitePlayerId = :playerId OR g.blackPlayerId = :playerId) " +
           "AND (:gameType IS NULL OR g.gameType = :gameType) " +
           "AND (:result IS NULL OR g.result = :result) " +
           "ORDER BY g.playedAt DESC")
    Page<Game> findByPlayerIdWithFilters(
            @Param("playerId") Long playerId,
            @Param("gameType") GameType gameType,
            @Param("result") String result,
            Pageable pageable
    );
}
