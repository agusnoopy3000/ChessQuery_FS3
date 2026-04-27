package cl.chessquery.users.repository;

import cl.chessquery.users.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByFederationId(String federationId);

    Optional<Player> findByFideId(String fideId);

    Optional<Player> findByRut(String rut);

    @Query(value = """
            SELECT p.* FROM player p
            WHERE  LOWER(p.first_name || ' ' || p.last_name) =
                   LOWER(:firstName || ' ' || :lastName)
            LIMIT  1
            """, nativeQuery = true)
    Optional<Player> findByFullNameIgnoreCase(
            @Param("firstName") String firstName,
            @Param("lastName") String lastName
    );

    /**
     * Búsqueda fuzzy usando pg_trgm similarity() sobre nombre completo,
     * con fallback exacto para RUT y FIDE ID.
     * Requiere la extensión pg_trgm activa (habilitada en V3__create_player.sql).
     */
    @Query(value = """
            SELECT p.*
            FROM   player p
            WHERE  similarity(LOWER(p.first_name || ' ' || p.last_name), LOWER(:q)) > 0.2
                OR LOWER(p.rut)     = LOWER(:q)
                OR LOWER(p.fide_id) = LOWER(:q)
            ORDER BY similarity(LOWER(p.first_name || ' ' || p.last_name), LOWER(:q)) DESC
            LIMIT  :limit
            """, nativeQuery = true)
    List<Player> searchFuzzy(@Param("q") String q, @Param("limit") int limit);

    /**
     * Ranking por ELO nacional, filtrado por región y rango de fecha de nacimiento
     * (para categorías de edad).
     */
    @Query(value = """
            SELECT p.* FROM player p
            WHERE  (CAST(:region AS text) IS NULL OR LOWER(p.region) = LOWER(CAST(:region AS text)))
            AND    (CAST(:minBirth AS date) IS NULL OR p.birth_date >= CAST(:minBirth AS date))
            AND    (CAST(:maxBirth AS date) IS NULL OR p.birth_date <= CAST(:maxBirth AS date))
            AND    p.elo_national IS NOT NULL
            ORDER  BY p.elo_national DESC
            """, nativeQuery = true)
    List<Player> findRanking(
            @Param("region")   String region,
            @Param("minBirth") LocalDate minBirth,
            @Param("maxBirth") LocalDate maxBirth,
            org.springframework.data.domain.Pageable pageable
    );
}
