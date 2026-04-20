package cl.chessquery.users.repository;

import cl.chessquery.users.entity.RatingHistory;
import cl.chessquery.users.entity.RatingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    List<RatingHistory> findByPlayerIdAndRatingTypeOrderByRecordedAtDesc(
            Long playerId, RatingType ratingType);

    /** Último registro de ELO de un tipo dado para calcular el delta. */
    @Query("""
            SELECT rh FROM RatingHistory rh
            WHERE  rh.player.id = :playerId AND rh.ratingType = :type
            ORDER  BY rh.recordedAt DESC
            LIMIT  1
            """)
    Optional<RatingHistory> findLatest(@Param("playerId") Long playerId,
                                       @Param("type") RatingType type);
}
