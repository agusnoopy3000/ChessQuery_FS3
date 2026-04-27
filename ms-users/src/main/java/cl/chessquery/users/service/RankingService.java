package cl.chessquery.users.service;

import cl.chessquery.users.dto.RankingEntryResponse;
import cl.chessquery.users.entity.AgeCategory;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.repository.PlayerTitleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final PlayerRepository            playerRepo;
    private final PlayerTitleHistoryRepository titleRepo;

    /**
     * Ranking nacional filtrado por categoría de edad y región.
     *
     * @param category nombre del enum AgeCategory (null = sin filtro por edad)
     * @param region   nombre de la región (null = sin filtro)
     * @param limit    máximo de resultados (tope: 200)
     */
    @Transactional(readOnly = true)
    public List<RankingEntryResponse> getRanking(String category, String region, int limit) {
        int safeLimit = Math.min(limit, 200);

        AgeCategory cat = parseCategory(category);

        List<Player> players = playerRepo.findRanking(
                region,
                cat != null ? cat.minBirthDate() : null,
                cat != null ? cat.maxBirthDate() : null,
                PageRequest.of(0, safeLimit)
        );

        AtomicInteger pos = new AtomicInteger(1);
        return players.stream()
                .map(p -> {
                    String title = titleRepo.findByPlayerIdAndIsCurrentTrue(p.getId())
                            .map(t -> t.getTitle().name())
                            .orElse(null);
                    AgeCategory playerCat = AgeCategory.fromBirthDate(p.getBirthDate());
                    return new RankingEntryResponse(
                            pos.getAndIncrement(),
                            p.getId(),
                            p.getFirstName(),
                            p.getLastName(),
                            p.getRegion(),
                            p.getClub() != null ? p.getClub().getName() : null,
                            p.getEloNational(),
                            p.getEloFideStandard(),
                            title,
                            playerCat.name(),
                            p.getEnrichmentSource()
                    );
                })
                .toList();
    }

    private AgeCategory parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return AgeCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
