package cl.chessquery.users.service;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.entity.*;
import cl.chessquery.users.exception.ApiException;
import cl.chessquery.users.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository            playerRepo;
    private final ClubRepository              clubRepo;
    private final RatingHistoryRepository     historyRepo;
    private final PlayerTitleHistoryRepository titleRepo;
    private final EventPublisherService       events;

    @PersistenceContext
    private EntityManager em;

    // ─── POST /users/sync (upsert post-registro) ─────────────────────────────

    /**
     * Crea, reclama o actualiza el Player con id=auth.userId. Flujo:
     * <ol>
     *   <li>Si ya existe Player con id=auth.userId → UPDATE de los campos
     *       no nulos del request (idempotente).</li>
     *   <li>Si no, busca un Player <em>federado-solamente</em> (email NULL)
     *       cuyo nombre coincida con el del request. Si encuentra match,
     *       lo "reclama": re-asigna su id a auth.userId (cascada vía FK
     *       ON UPDATE) y rellena email/lichessUsername aportados por el
     *       registro. Esto evita duplicar perfiles cuando AJEFECH ya
     *       había scrapeado al jugador.</li>
     *   <li>Si tampoco hay match federado → INSERT nuevo con el id explícito.</li>
     * </ol>
     */
    @Transactional
    public PlayerProfileResponse syncFromAuth(AuthSyncRequest req) {
        // 1. Player ya existe con ese authId → UPDATE
        Player existing = playerRepo.findById(req.id()).orElse(null);
        if (existing != null) {
            applyAuthFields(existing, req);
            playerRepo.save(existing);
        } else {
            // 2. Intentar reclamar Player federado-solo por nombre
            Player claimed = tryClaimFederated(req);
            if (claimed != null) {
                applyAuthFields(claimed, req);
                playerRepo.save(claimed);
                log.info("Player federado reclamado: id {} → {} (auth_user)",
                        req.id(), claimed.getId());
            } else {
                // 3. INSERT nativo con id explícito desde auth_user
                Instant now = Instant.now();
                em.createNativeQuery("""
                        INSERT INTO player (id, first_name, last_name, email, lichess_username, created_at, updated_at)
                        OVERRIDING SYSTEM VALUE
                        VALUES (:id, :fn, :ln, :em, :lu, :ts, :ts)
                        ON CONFLICT (id) DO NOTHING
                        """)
                        .setParameter("id", req.id())
                        .setParameter("fn", StringUtils.hasText(req.firstName()) ? req.firstName() : "Jugador")
                        .setParameter("ln", StringUtils.hasText(req.lastName())  ? req.lastName()  : String.valueOf(req.id()))
                        .setParameter("em", req.email())
                        .setParameter("lu", StringUtils.hasText(req.lichessUsername()) ? req.lichessUsername().trim() : null)
                        .setParameter("ts", now)
                        .executeUpdate();
                em.clear();
            }
        }

        Player p = playerRepo.findById(req.id())
                .orElseThrow(() -> new ApiException(500, "SYNC_FAILED", "No se pudo sincronizar el Player"));
        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(p.getId())
                .map(t -> t.getTitle().name()).orElse(null);
        return toProfileResponse(p, title);
    }

    /**
     * Busca un Player federado-solamente (sin email) cuyo nombre coincida
     * con el del request y re-asigna su id a auth.userId. Devuelve el
     * Player ya con el nuevo id, listo para que el caller aplique los
     * campos de auth (email, lichess) sin pisar los datos federados.
     * <p>
     * Si {@code req.id()} ya está ocupado por otro Player (carrera) o si
     * no hay nombre en el request, retorna {@code null}.
     */
    private Player tryClaimFederated(AuthSyncRequest req) {
        if (!StringUtils.hasText(req.firstName()) || !StringUtils.hasText(req.lastName())) {
            return null;
        }
        Player federated = playerRepo
                .findFederatedByFullName(req.firstName().trim(), req.lastName().trim())
                .orElse(null);
        if (federated == null) return null;

        Long oldId = federated.getId();
        if (oldId.equals(req.id())) return federated;

        // Re-asignar el id. Las FKs en rating_history y player_title_history
        // tienen ON UPDATE CASCADE (V8) — la actualización se propaga.
        int rows = em.createNativeQuery("UPDATE player SET id = :newId WHERE id = :oldId")
                .setParameter("newId", req.id())
                .setParameter("oldId", oldId)
                .executeUpdate();
        if (rows == 0) {
            log.warn("Reclamo abortado: no se actualizó player id {} → {}", oldId, req.id());
            return null;
        }
        em.clear();
        return playerRepo.findById(req.id()).orElse(null);
    }

    /**
     * Aplica los campos provenientes de auth (firstName, lastName, email,
     * lichessUsername) sin pisar datos federados ya curados (no
     * sobreescribe email si ya existe).
     */
    private void applyAuthFields(Player p, AuthSyncRequest req) {
        if (StringUtils.hasText(req.firstName())) p.setFirstName(req.firstName());
        if (StringUtils.hasText(req.lastName()))  p.setLastName(req.lastName());
        if (StringUtils.hasText(req.email()) && p.getEmail() == null) p.setEmail(req.email());
        if (StringUtils.hasText(req.lichessUsername()) && p.getLichessUsername() == null) {
            p.setLichessUsername(req.lichessUsername().trim());
        }
    }

    // ─── GET /users/{id}/profile ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfile(Long id) {
        Player p = findOrThrow(id);
        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(id)
                .map(t -> t.getTitle().name())
                .orElse(null);
        return toProfileResponse(p, title);
    }

    // ─── POST /users/provision (auto-provision desde JWT) ────────────────────

    /**
     * Crea o reclama idempotentemente un Player a partir de la identidad de
     * Supabase Auth. El API Gateway lo llama cuando un JWT válido entra y aún
     * no existe Player asociado (típicamente registro reciente cuyo webhook
     * no ha sido procesado, o webhook deshabilitado en local).
     *
     * <p>Reusa la misma lógica que {@code UserRegisteredConsumer}:
     * <ol>
     *   <li>Match por {@code supabaseUserId} → no-op.</li>
     *   <li>Match por email → asocia el UUID al Player existente.</li>
     *   <li>Si no, crea Player nuevo.</li>
     * </ol>
     *
     * Es idempotente: llamar dos veces con el mismo UUID retorna el mismo Player.
     * Robusto a concurrencia: si dos requests llegan simultáneas, el segundo
     * captura la unique violation y devuelve el Player creado por el primero.
     */
    @Transactional(noRollbackFor = org.springframework.dao.DataIntegrityViolationException.class)
    public PlayerProfileResponse provisionBySupabaseId(ProvisionPlayerRequest req) {
        UUID supabaseUserId = req.supabaseUserId();

        // 1. Idempotencia: ya existe Player con ese UUID → return as-is.
        Player existing = playerRepo.findBySupabaseUserId(supabaseUserId).orElse(null);
        if (existing != null) {
            String title = titleRepo.findByPlayerIdAndIsCurrentTrue(existing.getId())
                    .map(t -> t.getTitle().name()).orElse(null);
            return toProfileResponse(existing, title);
        }

        Club club = StringUtils.hasText(req.clubName())
                ? clubRepo.findFirstByNameIgnoreCase(req.clubName().trim()).orElse(null)
                : null;

        // 2. Match por email → asociar.
        if (StringUtils.hasText(req.email())) {
            Player byEmail = playerRepo.findByEmail(req.email()).orElse(null);
            if (byEmail != null) {
                byEmail.setSupabaseUserId(supabaseUserId);
                if (StringUtils.hasText(req.lichessUsername())
                        && !StringUtils.hasText(byEmail.getLichessUsername())) {
                    byEmail.setLichessUsername(req.lichessUsername().trim());
                }
                if (club != null && byEmail.getClub() == null) {
                    byEmail.setClub(club);
                }
                playerRepo.save(byEmail);
                log.info("provision: asociado supabaseUserId={} a Player existente id={}",
                        supabaseUserId, byEmail.getId());
                String title = titleRepo.findByPlayerIdAndIsCurrentTrue(byEmail.getId())
                        .map(t -> t.getTitle().name()).orElse(null);
                return toProfileResponse(byEmail, title);
            }
        }

        // 3. Crear nuevo Player. Bajo concurrencia (varias llamadas al
        //    mismo tiempo para el mismo supabaseUserId, típico cuando el
        //    frontend dispara N requests en paralelo apenas hay sesión)
        //    el primer INSERT gana y los demás chocan con la unique de
        //    supabase_user_id o email. Capturamos y re-leemos para
        //    devolver el Player ya creado por el ganador.
        Instant now = Instant.now();
        String firstName = StringUtils.hasText(req.firstName()) ? req.firstName() : "Jugador";
        String lastName  = StringUtils.hasText(req.lastName())  ? req.lastName()
                : supabaseUserId.toString().substring(0, 8);
        Player p = Player.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(StringUtils.hasText(req.email()) ? req.email() : null)
                .supabaseUserId(supabaseUserId)
                .lichessUsername(StringUtils.hasText(req.lichessUsername())
                        ? req.lichessUsername().trim() : null)
                .club(club)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            playerRepo.saveAndFlush(p);
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            log.info("provision: race detectada para supabaseUserId={}, re-leyendo Player ganador",
                    supabaseUserId);
            em.clear();
            Player winner = playerRepo.findBySupabaseUserId(supabaseUserId)
                    .or(() -> StringUtils.hasText(req.email())
                            ? playerRepo.findByEmail(req.email())
                            : java.util.Optional.empty())
                    .orElseThrow(() -> dup);
            String wTitle = titleRepo.findByPlayerIdAndIsCurrentTrue(winner.getId())
                    .map(t -> t.getTitle().name()).orElse(null);
            return toProfileResponse(winner, wTitle);
        }
        log.info("provision: Player creado id={} supabaseUserId={} email={} club={}",
                p.getId(), supabaseUserId, p.getEmail(), club != null ? club.getName() : "—");
        return toProfileResponse(p, null);
    }

    // ─── GET /users/by-supabase-id/{supabaseUserId} ──────────────────────────

    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfileByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ApiException(400, "INVALID_EMAIL", "El parámetro 'email' es obligatorio");
        }
        Player p = playerRepo.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(404, "PLAYER_NOT_FOUND",
                        "Jugador con email " + email + " no encontrado"));
        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(p.getId())
                .map(t -> t.getTitle().name()).orElse(null);
        return toProfileResponse(p, title);
    }

    @Transactional(readOnly = true)
    public PlayerProfileResponse getProfileBySupabaseId(UUID supabaseUserId) {
        Player p = playerRepo.findBySupabaseUserId(supabaseUserId)
                .orElseThrow(() -> new ApiException(404, "PLAYER_NOT_FOUND",
                        "Jugador con supabaseUserId " + supabaseUserId + " no encontrado"));
        String title = titleRepo.findByPlayerIdAndIsCurrentTrue(p.getId())
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
                p.getFederationId(),
                p.getLichessUsername(),
                p.getEnrichmentSource(),
                p.getEnrichedAt(),
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
                p.getFederationId(),
                p.getRut(),
                p.getCountry() != null ? p.getCountry().getIsoCode() : null,
                p.getEloNational(),
                p.getEloFideStandard(),
                p.getEloPlatform(),
                title,
                p.getClub() != null ? p.getClub().getName() : null,
                p.getEnrichmentSource()
        );
    }
}
