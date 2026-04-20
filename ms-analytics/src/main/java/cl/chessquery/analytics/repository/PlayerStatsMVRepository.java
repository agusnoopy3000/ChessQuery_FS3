package cl.chessquery.analytics.repository;

import cl.chessquery.analytics.entity.PlayerStatsMV;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerStatsMVRepository extends JpaRepository<PlayerStatsMV, Long> {

    Page<PlayerStatsMV> findAllByOrderByWinRateDesc(Pageable pageable);
}
