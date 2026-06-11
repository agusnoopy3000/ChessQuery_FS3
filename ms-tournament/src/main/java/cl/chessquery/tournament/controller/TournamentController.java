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

        requireOrganizerRole(userRole, "Solo los organizadores pueden crear torneos");

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

    @Operation(summary = "Eliminar torneo (solo organizador dueño o admin). " +
            "Bloqueado si el torneo está IN_PROGRESS o ya tiene rondas/partidas asociadas.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTournament(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        requireOrganizerRole(userRole, "Solo los organizadores pueden eliminar torneos");
        tournamentService.deleteTournament(id, userId, isAdmin(userRole));
    }

    // ── Transición de estado ──────────────────────────────────────────────────

    @Operation(summary = "Cambiar el estado del torneo (DRAFT→OPEN→IN_PROGRESS→FINISHED). " +
            "Solo el organizador dueño o un admin.")
    @PatchMapping("/{id}/status")
    public TournamentResponse transitionStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusTransitionRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        requireOrganizerRole(userRole, "Solo los organizadores pueden cambiar el estado del torneo");
        tournamentService.assertCanManage(id, userId, isAdmin(userRole));
        return tournamentService.transitionStatus(id, request.newStatus());
    }

    // ── Inscripciones ─────────────────────────────────────────────────────────

    @Operation(summary = "Inscribir jugador en el torneo. Un jugador solo puede inscribirse " +
            "a sí mismo; inscribir a terceros requiere ser el organizador dueño (o admin).")
    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse joinTournament(
            @PathVariable Long id,
            @Valid @RequestBody JoinTournamentRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        boolean self = userId != null && userId.equals(request.playerId());
        if (!self) {
            requireOrganizerRole(userRole,
                    "Solo puedes inscribirte a ti mismo; inscribir a otro jugador requiere ser organizador");
            tournamentService.assertCanManage(id, userId, isAdmin(userRole));
        }
        return tournamentService.joinTournament(id, request.playerId());
    }

    @Operation(summary = "Listar inscripciones de un torneo")
    @GetMapping("/{id}/registrations")
    public List<RegistrationResponse> listRegistrations(@PathVariable Long id) {
        return tournamentService.listRegistrations(id);
    }

    @Operation(summary = "Aprobar una inscripción PENDING (solo el organizador dueño o admin)")
    @PatchMapping("/registrations/{registrationId}/approve")
    public RegistrationResponse approveRegistration(
            @PathVariable Long registrationId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireOrganizerRole(userRole, "Solo los organizadores pueden aprobar inscripciones");
        tournamentService.assertCanManageRegistration(registrationId, userId, isAdmin(userRole));
        return tournamentService.approveRegistration(registrationId);
    }

    @Operation(summary = "Rechazar una inscripción PENDING (solo el organizador dueño o admin)")
    @PatchMapping("/registrations/{registrationId}/reject")
    public RegistrationResponse rejectRegistration(
            @PathVariable Long registrationId,
            @RequestBody(required = false) RejectRegistrationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireOrganizerRole(userRole, "Solo los organizadores pueden rechazar inscripciones");
        tournamentService.assertCanManageRegistration(registrationId, userId, isAdmin(userRole));
        String reason = request == null ? null : request.reason();
        return tournamentService.rejectRegistration(registrationId, reason);
    }

    // ── Rondas ────────────────────────────────────────────────────────────────

    @Operation(summary = "Generar una ronda del torneo con emparejamientos automáticos " +
            "(solo el organizador dueño o admin)")
    @PostMapping("/{id}/rounds/{roundNumber}")
    @ResponseStatus(HttpStatus.CREATED)
    public RoundResponse generateRound(
            @PathVariable Long id,
            @PathVariable int roundNumber,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        requireOrganizerRole(userRole, "Solo los organizadores pueden generar rondas");
        tournamentService.assertCanManage(id, userId, isAdmin(userRole));
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

    @Operation(summary = "Registrar resultado manual de una mesa (solo el organizador dueño " +
            "o admin). El resultado automático de partidas en vivo entra por el consumer " +
            "de game.finished, no por este endpoint.")
    @PatchMapping("/pairings/{pairingId}/result")
    public PairingResponse recordResult(
            @PathVariable Long pairingId,
            @Valid @RequestBody PairingResultRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireOrganizerRole(userRole, "Solo los organizadores pueden registrar resultados");
        tournamentService.assertCanManagePairing(pairingId, userId, isAdmin(userRole));
        return tournamentService.recordResult(pairingId, request.result());
    }

    // ── Standings ─────────────────────────────────────────────────────────────

    @Operation(summary = "Obtener clasificación actual del torneo (con Buchholz y Sonneborn-Berger)")
    @GetMapping("/{id}/standings")
    public List<StandingEntry> getStandings(@PathVariable Long id) {
        return tournamentService.getStandings(id);
    }

    // ── Helpers de autorización ───────────────────────────────────────────────

    private static void requireOrganizerRole(String userRole, String message) {
        if (!"ORGANIZER".equalsIgnoreCase(userRole) && !isAdmin(userRole)) {
            throw new ApiException(403, "FORBIDDEN", message);
        }
    }

    private static boolean isAdmin(String userRole) {
        return "ADMIN".equalsIgnoreCase(userRole);
    }
}
