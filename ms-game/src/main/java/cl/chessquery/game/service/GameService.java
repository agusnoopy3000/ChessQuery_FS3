package cl.chessquery.game.service;

import cl.chessquery.game.dto.*;
import cl.chessquery.game.elo.EloCalculator;
import cl.chessquery.game.elo.EloResult;
import cl.chessquery.game.entity.Game;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.entity.Opening;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.opening.OpeningDetector;
import cl.chessquery.game.repository.GameRepository;
import cl.chessquery.game.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository       gameRepo;
    private final EloCalculator        eloCalculator;
    private final OpeningDetector      openingDetector;
    private final StorageService       storageService;
    private final EventPublisherService events;

    // ── Registrar partida ─────────────────────────────────────────────────────

    @Transactional
    public GameResponse registerGame(RegisterGameRequest req) {
        // 1. Calcular ELO
        // NOTA: totalGames=0 → K=32 siempre (integración MS-Analytics pendiente)
        int whiteEloBeforeVal = req.whiteEloBefore() != null ? req.whiteEloBefore() : 1500;
        int blackEloBeforeVal = req.blackEloBefore() != null ? req.blackEloBefore() : 1500;

        EloResult eloResult = eloCalculator.calculate(
                whiteEloBeforeVal, blackEloBeforeVal,
                req.result(),
                0, 0  // totalGames=0 → K=32 siempre
        );

        // 2. Detectar apertura si hay PGN
        Opening opening = null;
        if (req.pgnContent() != null && !req.pgnContent().isBlank()) {
            opening = openingDetector.detectOpening(req.pgnContent()).orElse(null);
        }

        // 3. Construir entidad para obtener ID (persistir primero sin storage key)
        Game game = Game.builder()
                .whitePlayerId(req.whitePlayerId())
                .blackPlayerId(req.blackPlayerId())
                .result(req.result())
                .gameType(req.gameType())
                .whiteEloBefore(whiteEloBeforeVal)
                .blackEloBefore(blackEloBeforeVal)
                .whiteEloAfter(eloResult.whiteNewElo())
                .blackEloAfter(eloResult.blackNewElo())
                .totalMoves(req.totalMoves())
                .opening(opening)
                .tournamentPairingId(req.tournamentPairingId())
                .durationSeconds(req.durationSeconds())
                .playedAt(req.playedAt() != null ? req.playedAt() : Instant.now())
                .build();

        gameRepo.save(game);

        // 4. Subir PGN a Storage si hay contenido. Si falla → 503 (la partida
        // ya quedó persistida; el usuario puede reintentar pegándole de nuevo,
        // pero NO publicamos eventos hasta confirmar el storage).
        if (req.pgnContent() != null && !req.pgnContent().isBlank()) {
            try {
                byte[] pgnBytes = req.pgnContent().getBytes(StandardCharsets.UTF_8);
                String storageKey = buildPgnKey(game.getId());
                storageService.uploadPgn(storageKey, pgnBytes);
                game.setPgnStorageKey(storageKey);
                gameRepo.save(game);
            } catch (Exception e) {
                log.error("Storage inaccesible al subir PGN gameId={}: {}",
                        game.getId(), e.getMessage());
                throw new ApiException(503, "STORAGE_UNAVAILABLE",
                        "No se pudo subir el PGN al storage");
            }
        }

        // 5. Publicar game.finished
        events.publishGameFinished(game);

        // 6. Publicar elo.updated x2 (uno por jugador).
        //    Usamos ratingType PLATFORM: el ELO ganado jugando en ChessQuery se
        //    refleja en eloPlatform y NO pisa el eloNational/eloFide que provee el
        //    sync federativo (ETL). Cada fuente de rating mantiene su propio campo.
        events.publishEloUpdated(
                req.whitePlayerId(), whiteEloBeforeVal, eloResult.whiteNewElo(), game.getId(), "PLATFORM");
        events.publishEloUpdated(
                req.blackPlayerId(), blackEloBeforeVal, eloResult.blackNewElo(), game.getId(), "PLATFORM");

        log.info("Partida registrada: id={} {}-{} result={} whiteElo={}→{} blackElo={}→{}",
                game.getId(), req.whitePlayerId(), req.blackPlayerId(), req.result(),
                whiteEloBeforeVal, eloResult.whiteNewElo(),
                blackEloBeforeVal, eloResult.blackNewElo());

        return toResponse(game);
    }

    // ── Obtener partida ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GameResponse getGame(Long id) {
        Game game = findOrThrow(id);
        return toResponse(game);
    }

    // ── Listar partidas ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<GameResponse> listGames(Long playerId, GameType gameType, String result, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Game> gamePage;

        if (playerId != null) {
            gamePage = gameRepo.findByPlayerIdWithFilters(playerId, gameType, result, pageable);
        } else {
            gamePage = gameRepo.findAll(pageable);
        }

        return new PageResponse<>(
                gamePage.getContent().stream().map(this::toResponse).toList(),
                gamePage.getNumber(),
                gamePage.getSize(),
                gamePage.getTotalElements(),
                gamePage.getTotalPages()
        );
    }

    // ── URL presignada del PGN ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PgnUrlResponse getPgnUrl(Long id) {
        Game game = findOrThrow(id);
        if (game.getPgnStorageKey() == null || game.getPgnStorageKey().isBlank()) {
            throw new ApiException(404, "PGN_NOT_FOUND",
                    "La partida " + id + " no tiene un PGN almacenado");
        }
        try {
            String url = storageService.generatePresignedUrl(game.getPgnStorageKey());
            return new PgnUrlResponse(url, Instant.now().plusSeconds(3600));
        } catch (Exception e) {
            log.error("Error generando URL presignada para partida {}: {}", id, e.getMessage());
            throw new ApiException(500, "PRESIGNED_URL_ERROR",
                    "No se pudo generar la URL para el PGN");
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Construye la storage key del PGN: games/{year}/{month}/{gameId}.pgn */
    private String buildPgnKey(Long gameId) {
        LocalDate now = LocalDate.now();
        return String.format("games/%d/%02d/%d.pgn",
                now.getYear(), now.getMonthValue(), gameId);
    }

    private Game findOrThrow(Long id) {
        return gameRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "GAME_NOT_FOUND",
                        "Partida con id " + id + " no encontrada"));
    }

    private GameResponse toResponse(Game g) {
        String pgnUrl = null;
        if (g.getPgnStorageKey() != null && !g.getPgnStorageKey().isBlank()) {
            try {
                pgnUrl = storageService.generatePresignedUrl(g.getPgnStorageKey());
            } catch (Exception e) {
                log.warn("No se pudo generar URL presignada para partida {}: {}", g.getId(), e.getMessage());
            }
        }

        return new GameResponse(
                g.getId(),
                g.getWhitePlayerId(),
                g.getBlackPlayerId(),
                g.getResult(),
                g.getGameType().name(),
                g.getWhiteEloBefore(),
                g.getBlackEloBefore(),
                g.getWhiteEloAfter(),
                g.getBlackEloAfter(),
                g.getTotalMoves(),
                g.getOpening() != null ? g.getOpening().getEcoCode() : null,
                g.getOpening() != null ? g.getOpening().getName() : null,
                pgnUrl,
                g.getTournamentPairingId(),
                g.getDurationSeconds(),
                g.getPlayedAt(),
                g.getCreatedAt()
        );
    }
}
