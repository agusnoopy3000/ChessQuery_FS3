package cl.chessquery.users.repository;

import cl.chessquery.users.entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClubRepository extends JpaRepository<Club, Integer> {
    List<Club> findByCountryIsoCode(String isoCode);
}
