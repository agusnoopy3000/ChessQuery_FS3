package cl.chessquery.analytics.service;

import cl.chessquery.analytics.dto.*;
import cl.chessquery.analytics.entity.GameRecord;
import cl.chessquery.analytics.entity.PlayerStatsMV;
import cl.chessquery.analytics.exception.ApiException;
import cl.chessquery.analytics.repository.GameRecordRepository;
import cl.chessquery.analytics.repository.PlayerStatsMVRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PlayerStatsMVRepository playerStatsRepo;
    private final GameRecordRepository    gameRecordRepo;

    @Transactional(readOnly = true)
    public PlayerStatsResponse getPlayerStats(Long playerId) {
        PlayerStatsMV stats = playerStatsRepo.findById(playerId)
                .orElseThrow(() -> new ApiException(404, "NOT_FOUND",
                        "No se encontraron estadísticas para el jugador " + playerId));
        return toResponse(stats);
    }

    @Transactional(readOnly = true)
    public HeadToHeadResponse getHeadToHead(Long playerId, Long opponentId) {
        List<GameRecord> games = gameRecordRepo.findHeadToHead(playerId, opponentId);

        int wins = 0, losses = 0, draws = 0;
        for (GameRecord g : games) {
            boolean isWhite = g.getWhitePlayerId().equals(playerId);
            if ("1/2-1/2".equals(g.getResult())) {
                draws++;
            } else if (("1-0".equals(g.getResult()) && isWhite) ||
                       ("0-1".equals(g.getResult()) && !isWhite)) {
                wins++;
            } else {
                losses++;
            }
        }

        return new HeadToHeadResponse(playerId, opponentId, games.size(), wins, losses, draws);
    }

    @Transactional(readOnly = true)
    public List<OpeningStatsEntry> getOpeningStats(Long playerId) {
        List<Object[]> rows = gameRecordRepo.findOpeningStatsByPlayer(playerId);
        return rows.stream()
                .map(row -> new OpeningStatsEntry(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformSummaryResponse getPlatformSummary() {
        long totalPlayers = playerStatsRepo.count();
        long totalGames   = gameRecordRepo.count();
        // activeTournaments: simplificado — requiere MS-Tournament o tabla auxiliar
        return new PlatformSummaryResponse(totalPlayers, totalGames, 0L);
    }

    private PlayerStatsResponse toResponse(PlayerStatsMV s) {
        return new PlayerStatsResponse(
                s.getPlayerId(),
                s.getTotalGames(),
                s.getWins(),
                s.getLosses(),
                s.getDraws(),
                s.getWinRate(),
                s.getAvgMoves(),
                s.getCurrentStreak(),
                s.getBestElo(),
                s.getLastRefreshed()
        );
    }
}
