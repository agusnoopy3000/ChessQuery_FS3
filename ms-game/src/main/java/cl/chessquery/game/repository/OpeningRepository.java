package cl.chessquery.game.repository;

import cl.chessquery.game.entity.Opening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OpeningRepository extends JpaRepository<Opening, Integer> {

    /**
     * Encuentra la apertura más específica cuyos movimientos son prefijo de los movimientos dados.
     * Ordena por LENGTH DESC para obtener la coincidencia más larga (más específica).
     */
    @Query(value = "SELECT * FROM opening WHERE :moves LIKE CONCAT(pgn_moves, '%') " +
                   "ORDER BY LENGTH(pgn_moves) DESC LIMIT 1", nativeQuery = true)
    Optional<Opening> findBestMatch(@Param("moves") String moves);

    Optional<Opening> findByEcoCode(String ecoCode);
}
