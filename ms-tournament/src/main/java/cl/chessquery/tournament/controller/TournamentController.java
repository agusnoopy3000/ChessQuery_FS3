package cl.chessquery.tournament.controller;

import cl.chessquery.tournament.dto.*;
import cl.chessquery.tournament.entity.TournamentFormat;
import cl.chessquery.tournament.entity.TournamentStatus;
import cl.chessquery.tournament.exception.ApiException;
import cl.chessquery.tournament.service.TournamentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournaments")
@RequiredArgsConstructor
@Tag(name = "Tournaments", description = "Gestión de torneos, inscripciones y rondas")
public class TournamentController {

    private final TournamentService tournamentService;

    // ── Crear torneo ──────────────────────────────────────────────────────────

    @Operation(summary = "Crear un nuevo torneo (requiere rol ORGANIZER)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TournamentResponse createTournament(
            @Valid @RequestBody CreateTournamentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        if (!"ORGANIZER".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new ApiException(403, "FORBIDDEN",
                    "Solo los organizadores pueden crear torneos");
        }

        Long organizerId = userId != null ? userId
                : (request.organizerId() != null ? request.organizerId() : null);

        if (organizerId == null) {
            throw new ApiException(400, "MISSING_ORGANIZER",
                    "Se requiere el ID del organizador");
        }

        return tournamentService.createTournament(request, organizerId);
    }

    // ── Listar torneos ────────────────────────────────────────────────────────

    @Operation(summary = "Listar torneos con filtros opcionales de estado y formato")
    @GetMapping
    public PageResponse<TournamentResponse> listTournaments(
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) TournamentFormat format,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tournamentService.listTournaments(status, format, page, size);
    }

    // ── Obtener torneo ─────────────────────────────────────────────────────────

    @Operation(summary = "Obtener un torneo por ID")
    @GetMapping("/{id}")
    public TournamentResponse getTournament(@PathVariable Long id) {
        return tournamentService.getTournament(id);
    }

    // ── Transición de estado ──────────────────────────────────────────────────

    @Operation(summary = "Cambiar el estado del torneo (DRAFT→OPEN→IN_PROGRESS→FINISHED)")
    @PatchMapping("/{id}/status")
    public TournamentResponse transitionStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusTransitionRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"ORGANIZER".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new ApiException(403, "FORBIDDEN",
                    "Solo los organizadores pueden cambiar el estado del torneo");
        }

        return tournamentService.transitionStatus(id, request.newStatus());
    }

    // ── Inscripciones ─────────────────────────────────────────────────────────

    @Operation(summary = "Inscribir jugador en el torneo")
    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse joinTournament(
            @PathVariable Long id,
            @Valid @RequestBody JoinTournamentRequest request) {
        return tournamentService.joinTournament(id, request.playerId());
    }

    @Operation(summary = "Listar inscripciones de un torneo")
    @GetMapping("/{id}/registrations")
    public List<RegistrationResponse> listRegistrations(@PathVariable Long id) {
        return tournamentService.listRegistrations(id);
    }

    // ── Rondas ────────────────────────────────────────────────────────────────

    @Operation(summary = "Generar una ronda del torneo con emparejamientos automáticos")
    @PostMapping("/{id}/rounds/{roundNumber}")
    @ResponseStatus(HttpStatus.CREATED)
    public RoundResponse generateRound(
            @PathVariable Long id,
            @PathVariable int roundNumber,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"ORGANIZER".equalsIgnoreCase(userRole) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new ApiException(403, "FORBIDDEN",
                    "Solo los organizadores pueden generar rondas");
        }

        return tournamentService.generateRound(id, roundNumber);
    }

    @Operation(summary = "Obtener una ronda con sus emparejamientos")
    @GetMapping("/{id}/rounds/{roundNumber}")
    public RoundResponse getRound(
            @PathVariable Long id,
            @PathVariable int roundNumber) {
        return tournamentService.getRound(id, roundNumber);
    }

    // ── Resultado de emparejamiento ───────────────────────────────────────────

    @Operation(summary = "Registrar resultado de una partida en el torneo")
    @PatchMapping("/pairings/{pairingId}/result")
    public PairingResponse recordResult(
            @PathVariable Long pairingId,
            @Valid @RequestBody PairingResultRequest request) {
        return tournamentService.recordResult(pairingId, request.result());
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    @Operation(summary = "Obtener clasificación actual del torneo (con Buchholz y Sonneborn-Berger)")
    @GetMapping("/{id}/standings")
    public List<StandingEntry> getStandings(@PathVariable Long id) {
        return tournamentService.getStandings(id);
    }
}
