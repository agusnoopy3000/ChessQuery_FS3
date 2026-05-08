package cl.chessquery.game.controller;

import cl.chessquery.game.dto.LiveGameDtos.*;
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
}
