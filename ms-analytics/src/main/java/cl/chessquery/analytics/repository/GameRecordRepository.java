package cl.chessquery.analytics.repository;

import cl.chessquery.analytics.entity.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    List<GameRecord> findByWhitePlayerIdOrBlackPlayerId(Long whiteId, Long blackId);

    @Query("SELECT g FROM GameRecord g WHERE " +
           "(g.whitePlayerId = :p1 AND g.blackPlayerId = :p2) OR " +
           "(g.whitePlayerId = :p2 AND g.blackPlayerId = :p1)")
    List<GameRecord> findHeadToHead(@Param("p1") Long p1, @Param("p2") Long p2);

    @Query("SELECT g.openingId, COUNT(g) as total, " +
           "SUM(CASE WHEN (g.whitePlayerId = :pid AND g.result = '1-0') OR " +
           "(g.blackPlayerId = :pid AND g.result = '0-1') THEN 1 ELSE 0 END) as wins " +
           "FROM GameRecord g " +
           "WHERE (g.whitePlayerId = :pid OR g.blackPlayerId = :pid) " +
           "AND g.openingId IS NOT NULL " +
           "GROUP BY g.openingId")
    List<Object[]> findOpeningStatsByPlayer(@Param("pid") Long playerId);
}
