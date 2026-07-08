package cl.chessquery.game.controller;

import cl.chessquery.game.dto.LiveGameDtos.*;
import cl.chessquery.game.entity.GameInvitation;
import cl.chessquery.game.service.InvitationService;
import cl.chessquery.game.service.LiveGameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games/live")
@RequiredArgsConstructor
@Tag(name = "Live Games", description = "Partidas en tiempo real entre 2 jugadores")
public class LiveGameController {

    private final LiveGameService live;
    private final InvitationService invitations;

    @Operation(summary = "Crear sesión live (creador = white)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LiveGameResponse create(@Valid @RequestBody CreateLiveGameRequest req) {
        return live.create(req);
    }

    @Operation(summary = "Estado actual de la sesión + jugadas")
    @GetMapping("/{id}")
    public LiveGameResponse get(@PathVariable Long id) {
        return live.get(id);
    }

    @Operation(summary = "Sumarse a la partida como rival (black)")
    @PostMapping("/{id}/join")
    public LiveGameResponse join(@PathVariable Long id, @Valid @RequestBody JoinLiveGameRequest req) {
        return live.join(id, req);
    }

    @Operation(summary = "Registrar una jugada en UCI (e2e4, e7e8q…)")
    @PostMapping("/{id}/move")
    public LiveGameResponse move(@PathVariable Long id, @Valid @RequestBody MoveRequest req) {
        return live.move(id, req);
    }

    @Operation(summary = "Rendirse y cerrar la partida")
    @PostMapping("/{id}/resign")
    public LiveGameResponse resign(@PathVariable Long id, @Valid @RequestBody ResignRequest req) {
        return live.resign(id, req);
    }

    @Operation(summary = "Cerrar la partida en tablas por acuerdo mutuo (R11)")
    @PostMapping("/{id}/draw")
    public LiveGameResponse draw(@PathVariable Long id, @Valid @RequestBody ResignRequest req) {
        return live.drawAgreement(id, req);
    }

    @Operation(summary = "Reportar pérdida por tiempo (R5) — el oponente gana")
    @PostMapping("/{id}/timeout")
    public LiveGameResponse timeout(@PathVariable Long id, @Valid @RequestBody ResignRequest req) {
        return live.timeout(id, req);
    }

    @Operation(summary = "Revancha — crea nueva sesión con colores invertidos (status WAITING)")
    @PostMapping("/{id}/rematch")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveGameResponse rematch(@PathVariable Long id, @Valid @RequestBody RematchRequest req) {
        return live.rematch(id, req);
    }

    @Operation(summary = "Invitar a otro jugador por email — si tiene cuenta, recibe push in-app además del email")
    @PostMapping("/{id}/invite")
    public java.util.Map<String, Object> invite(
            @PathVariable Long id,
            @Valid @RequestBody InviteRequest req,
            @RequestHeader(value = "X-User-Id", required = false) Long inviterId) {
        return live.invitePlayer(id, req.email(), req.gameUrl(), inviterId);
    }

    @Operation(summary = "Crear invitación con TTL (P1-03) — envía push y arranca el countdown")
    @PostMapping("/{id}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse createInvitation(
            @PathVariable Long id,
            @Valid @RequestBody CreateInvitationRequest req,
            @RequestHeader(value = "X-User-Id", required = false) Long inviterId) {
        // Reusa el flujo existente para el push in-app y la resolución del email → playerId.
        java.util.Map<String, Object> push = live.invitePlayer(id, req.email(), req.gameUrl(), inviterId);
        Long toPlayerId = push.get("playerId") instanceof Number n ? n.longValue() : null;
        GameInvitation inv = invitations.create(id, inviterId, toPlayerId,
                req.email(), req.color(), req.ttlSeconds());
        return InvitationService.toResponse(inv);
    }

    @Operation(summary = "Estado de la invitación (para el countdown; expira perezosamente)")
    @GetMapping("/invitations/{inviteId}")
    public InvitationResponse getInvitation(@PathVariable Long inviteId) {
        return InvitationService.toResponse(invitations.get(inviteId));
    }

    @Operation(summary = "El invitado acepta la invitación")
    @PostMapping("/invitations/{inviteId}/accept")
    public InvitationResponse acceptInvitation(
            @PathVariable Long inviteId, @Valid @RequestBody InvitationActionRequest req) {
        return InvitationService.toResponse(invitations.accept(inviteId, req.playerId()));
    }

    @Operation(summary = "El invitado rechaza la invitación (motivo opcional)")
    @PostMapping("/invitations/{inviteId}/decline")
    public InvitationResponse declineInvitation(
            @PathVariable Long inviteId, @Valid @RequestBody InvitationActionRequest req) {
        return InvitationService.toResponse(invitations.decline(inviteId, req.playerId(), req.reason()));
    }
}
