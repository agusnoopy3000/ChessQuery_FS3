package cl.chessquery.analytics.controller;

import cl.chessquery.analytics.dto.*;
import cl.chessquery.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Estadísticas de jugadores y plataforma")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/players/{id}/stats")
    @Operation(summary = "Obtener estadísticas de un jugador")
    public ResponseEntity<PlayerStatsResponse> getPlayerStats(@PathVariable Long id) {
        return ResponseEntity.ok(analyticsService.getPlayerStats(id));
    }

    @GetMapping("/players/{id}/vs/{opponentId}")
    @Operation(summary = "Historial head-to-head entre dos jugadores")
    public ResponseEntity<HeadToHeadResponse> getHeadToHead(
            @PathVariable Long id,
            @PathVariable Long opponentId) {
        return ResponseEntity.ok(analyticsService.getHeadToHead(id, opponentId));
    }

    @GetMapping("/players/{id}/openings")
    @Operation(summary = "Estadísticas de aperturas por jugador")
    public ResponseEntity<List<OpeningStatsEntry>> getOpeningStats(@PathVariable Long id) {
        return ResponseEntity.ok(analyticsService.getOpeningStats(id));
    }

    @GetMapping("/platform/summary")
    @Operation(summary = "Resumen global de la plataforma")
    public ResponseEntity<PlatformSummaryResponse> getPlatformSummary() {
        return ResponseEntity.ok(analyticsService.getPlatformSummary());
    }
}
