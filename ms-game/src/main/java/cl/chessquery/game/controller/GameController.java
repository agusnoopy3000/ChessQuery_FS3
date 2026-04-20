package cl.chessquery.game.controller;

import cl.chessquery.game.dto.*;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
@Tag(name = "Games", description = "Registro de partidas, cálculo ELO y almacenamiento PGN")
public class GameController {

    private final GameService gameService;

    // ── Registrar partida ─────────────────────────────────────────────────────

    @Operation(summary = "Registrar una nueva partida con cálculo ELO automático")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse registerGame(@Valid @RequestBody RegisterGameRequest request) {
        return gameService.registerGame(request);
    }

    // ── Obtener partida ────────────────────────────────────────────────────────

    @Operation(summary = "Obtener partida por ID con URL presignada del PGN")
    @GetMapping("/{id}")
    public GameResponse getGame(@PathVariable Long id) {
        return gameService.getGame(id);
    }

    // ── Listar partidas ────────────────────────────────────────────────────────

    @Operation(summary = "Historial de partidas paginado con filtros opcionales")
    @GetMapping
    public PageResponse<GameResponse> listGames(
            @RequestParam(required = false) Long playerId,
            @RequestParam(required = false) GameType gameType,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return gameService.listGames(playerId, gameType, result, page, size);
    }

    // ── URL presignada PGN ────────────────────────────────────────────────────

    @Operation(summary = "Obtener URL presignada del PGN (expira en 1 hora)")
    @GetMapping("/{id}/pgn-url")
    public PgnUrlResponse getPgnUrl(@PathVariable Long id) {
        return gameService.getPgnUrl(id);
    }
}
