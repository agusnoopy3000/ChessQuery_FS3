package cl.chessquery.users.controller;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.service.PlayerService;
import cl.chessquery.users.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Perfiles de jugadores, búsqueda y rankings")
public class UserController {

    private final PlayerService  playerService;
    private final RankingService rankingService;
    private final PlayerRepository playerRepository;

    // ── Listado paginado ──────────────────────────────────────────────────────

    @Operation(summary = "Conteo y listado liviano de jugadores (uso del dashboard admin)")
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long total = playerRepository.count();
        return Map.of(
                "content", List.of(),
                "page", page,
                "size", size,
                "totalElements", total,
                "totalPages", size == 0 ? 0 : (int) Math.ceil((double) total / size)
        );
    }

    // ── Sincronización post-registro ─────────────────────────────────────────

    @Operation(summary = "Crea o actualiza el Player con id = auth_user.id tras /auth/register")
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.OK)
    public PlayerProfileResponse syncFromAuth(@Valid @RequestBody AuthSyncRequest request) {
        return playerService.syncFromAuth(request);
    }

    // ── Perfil completo ───────────────────────────────────────────────────────

    @Operation(summary = "Obtener perfil completo del jugador (country y club resueltos)")
    @GetMapping("/{id}/profile")
    public PlayerProfileResponse getProfile(@PathVariable Long id) {
        return playerService.getProfile(id);
    }

    @Operation(summary = "Resolver Player por UUID de Supabase Auth (usado por API Gateway / BFFs)")
    @GetMapping("/by-supabase-id/{supabaseUserId}")
    public PlayerProfileResponse getBySupabaseId(@PathVariable UUID supabaseUserId) {
        return playerService.getProfileBySupabaseId(supabaseUserId);
    }

    @Operation(summary = "Resolver Player por email (usado por ms-game para invitaciones in-app)")
    @GetMapping("/by-email")
    public PlayerProfileResponse getByEmail(@RequestParam String email) {
        return playerService.getProfileByEmail(email);
    }

    @Operation(summary = "Auto-provisionar Player desde JWT de Supabase (idempotente)",
               description = "Llamado por el API Gateway cuando un JWT válido entra pero el "
                       + "Player aún no existe (registro reciente, webhook caído).")
    @PostMapping("/provision")
    @ResponseStatus(HttpStatus.OK)
    public PlayerProfileResponse provision(@Valid @RequestBody ProvisionPlayerRequest request) {
        return playerService.provisionBySupabaseId(request);
    }

    @Operation(summary = "Actualizar datos de perfil (firstName, lastName, clubId, region)")
    @PutMapping("/{id}/profile")
    public PlayerProfileResponse updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileRequest request) {
        return playerService.updateProfile(id, request);
    }

    // ── Búsqueda fuzzy ────────────────────────────────────────────────────────

    @Operation(summary = "Búsqueda por nombre (fuzzy), RUT o FIDE ID")
    @GetMapping("/search")
    public List<PlayerSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        return playerService.search(q, limit);
    }

    // ── Historial de ratings ──────────────────────────────────────────────────

    @Operation(summary = "Historial de ELO por tipo, ordenado por fecha (para gráficos)")
    @GetMapping("/{id}/rating-history")
    public List<RatingHistoryResponse> ratingHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "NATIONAL") RatingType type) {
        return playerService.getRatingHistory(id, type);
    }

    // ── Ranking nacional ──────────────────────────────────────────────────────

    @Operation(summary = "Ranking nacional filtrado por categoría de edad y región")
    @GetMapping("/ranking")
    public List<RankingEntryResponse> ranking(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "50") int limit) {
        return rankingService.getRanking(category, region, limit);
    }

    // ── Endpoint interno: actualizar ELO ─────────────────────────────────────
    // Nota: Nginx NO expone esta ruta al exterior (/users/{id}/elo no tiene
    // mapping en nginx.conf). Solo es accesible dentro de la red Docker.

    @Operation(summary = "[INTERNO] Actualizar ELO de un jugador (usado por MS-ETL y MS-Game vía REST)")
    @PutMapping("/{id}/elo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateElo(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEloRequest request) {
        playerService.updateElo(id, request);
    }
}
