package cl.chessquery.users.service;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.entity.*;
import cl.chessquery.users.exception.ApiException;
import cl.chessquery.users.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository            playerRepo;
    private final ClubRepository              clubRepo;
    private final RatingHistoryRepository     historyRepo;
    private final PlayerTitleHistoryRepository titleRepo;
    private final EventPublisherService       events;

    // ─── GET /users/{id}/profile ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfile(Long id) {
        Player p = findOrThrow(id);
        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(id)
                .map(t -> t.getTitle().name())
                .orElse(null);
        return toProfileResponse(p, title);
    }

    // ─── GET /users/search ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlayerSearchResponse> search(String q, int limit) {
        if (!StringUtils.hasText(q)) {
            throw new ApiException(400, "INVALID_QUERY", "El parámetro 'q' no puede estar vacío");
        }
        int safeLimit = Math.min(limit, 50);
        return playerRepo.searchFuzzy(q.trim(), safeLimit).stream()
                .map(p -> {
                    String title = titleRepo.findByPlayerIdAndIsCurrentTrue(p.getId())
                            .map(t -> t.getTitle().name()).orElse(null);
                    return toSearchResponse(p, title);
                })
                .toList();
    }

    // ─── GET /users/{id}/rating-history ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RatingHistoryResponse> getRatingHistory(Long id, RatingType type) {
        findOrThrow(id);
        return historyRepo.findByPlayerIdAndRatingTypeOrderByRecordedAtDesc(id, type)
                .stream()
                .map(rh -> new RatingHistoryResponse(
                        rh.getId(),
                        rh.getRatingType().name(),
                        rh.getRatingValue(),
                        rh.getRatingPrevValue(),
                        rh.getDelta(),
                        rh.getRecordedAt(),
                        rh.getSource()))
                .toList();
    }

    // ─── PUT /users/{id}/profile ──────────────────────────────────────────────

    @Transactional
    public PlayerProfileResponse updateProfile(Long id, UpdateProfileRequest req) {
        Player p = findOrThrow(id);
        List<String> changed = new java.util.ArrayList<>();

        if (StringUtils.hasText(req.firstName())) {
            p.setFirstName(req.firstName());
            changed.add("firstName");
        }
        if (StringUtils.hasText(req.lastName())) {
            p.setLastName(req.lastName());
            changed.add("lastName");
        }
        if (req.clubId() != null) {
            Club club = clubRepo.findById(req.clubId())
                    .orElseThrow(() -> new ApiException(404, "CLUB_NOT_FOUND", "Club no encontrado"));
            p.setClub(club);
            changed.add("clubId");
        }
        if (req.region() != null) {
            p.setRegion(req.region());
            changed.add("region");
        }

        playerRepo.save(p);

        if (!changed.isEmpty()) {
            events.publishUserUpdated(p.getId(), changed);
        }

        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(id)
                .map(t -> t.getTitle().name()).orElse(null);
        return toProfileResponse(p, title);
    }

    // ─── PUT /users/{id}/elo (endpoint interno) ───────────────────────────────

    @Transactional
    public void updateElo(Long id, UpdateEloRequest req) {
        Player p = findOrThrow(id);

        int oldElo  = currentElo(p, req.ratingType());
        int newElo  = req.newValue();

        applyElo(p, req.ratingType(), newElo);
        playerRepo.save(p);

        RatingHistory history = RatingHistory.builder()
                .player(p)
                .ratingType(req.ratingType())
                .ratingValue(newElo)
                .ratingPrevValue(oldElo == 0 ? null : oldElo)
                .delta(oldElo == 0 ? null : (short) (newElo - oldElo))
                .recordedAt(Instant.now())
                .source(req.source())
                .build();
        historyRepo.save(history);

        events.publishEloUpdated(id, oldElo, newElo, req.ratingType(), null);
        log.info("ELO actualizado vía REST para jugador {}: {} → {} ({})",
                id, oldElo, newElo, req.ratingType());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Player findOrThrow(Long id) {
        return playerRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "PLAYER_NOT_FOUND",
                        "Jugador con id " + id + " no encontrado"));
    }

    private int currentElo(Player p, RatingType type) {
        Integer v = switch (type) {
            case NATIONAL      -> p.getEloNational();
            case FIDE_STANDARD -> p.getEloFideStandard();
            case FIDE_RAPID    -> p.getEloFideRapid();
            case FIDE_BLITZ    -> p.getEloFideBlitz();
            case PLATFORM      -> p.getEloPlatform();
        };
        return v != null ? v : 0;
    }

    private void applyElo(Player p, RatingType type, int value) {
        switch (type) {
            case NATIONAL      -> p.setEloNational(value);
            case FIDE_STANDARD -> p.setEloFideStandard(value);
            case FIDE_RAPID    -> p.setEloFideRapid(value);
            case FIDE_BLITZ    -> p.setEloFideBlitz(value);
            case PLATFORM      -> p.setEloPlatform(value);
        }
    }

    private PlayerProfileResponse toProfileResponse(Player p, String title) {
        return new PlayerProfileResponse(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getEmail(),
                p.getRut(),
                p.getBirthDate(),
                p.getGender(),
                p.getRegion(),
                p.getFideId(),
                p.getLichessUsername(),
                p.getCountry() != null
                        ? new CountryDto(p.getCountry().getId(), p.getCountry().getIsoCode(),
                                         p.getCountry().getName(), p.getCountry().getFideFederation())
                        : null,
                p.getClub() != null
                        ? new ClubDto(p.getClub().getId(), p.getClub().getName(),
                                      p.getClub().getCity(), p.getClub().getFederationCode())
                        : null,
                p.getEloNational(),
                p.getEloFideStandard(),
                p.getEloFideRapid(),
                p.getEloFideBlitz(),
                p.getEloPlatform(),
                title,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private PlayerSearchResponse toSearchResponse(Player p, String title) {
        return new PlayerSearchResponse(
                p.getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getFideId(),
                p.getRut(),
                p.getCountry() != null ? p.getCountry().getIsoCode() : null,
                p.getEloNational(),
                p.getEloFideStandard(),
                title
        );
    }
}
