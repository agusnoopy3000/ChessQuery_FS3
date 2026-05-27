package cl.chessquery.analytics.integration;

import cl.chessquery.analytics.entity.GameRecord;
import cl.chessquery.analytics.entity.PlayerStatsMV;
import cl.chessquery.analytics.repository.GameRecordRepository;
import cl.chessquery.analytics.repository.PlayerStatsMVRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración del controlador de analytics: levantan el contexto
 * Spring completo (H2 + JPA + perfil {@code test}) y validan que la lectura
 * por API refleje las estadísticas materializadas en {@code player_stats_mv}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integración HTTP + JPA — Analytics")
class AnalyticsIntegrationTest {

    @Autowired MockMvc                 mockMvc;
    @Autowired PlayerStatsMVRepository playerStatsRepo;
    @Autowired GameRecordRepository    gameRecordRepo;

    @BeforeEach
    void clean() {
        gameRecordRepo.deleteAll();
        playerStatsRepo.deleteAll();
    }

    @Test
    @DisplayName("GET /analytics/players/{id}/stats devuelve las stats materializadas")
    void getPlayerStats_existing_returnsResponse() throws Exception {
        playerStatsRepo.save(PlayerStatsMV.builder()
                .playerId(10L)
                .totalGames(5).wins(3).losses(1).draws(1)
                .winRate(new BigDecimal("60.00"))
                .avgMoves(new BigDecimal("38.5"))
                .currentStreak(2)
                .bestElo(1950)
                .lastRefreshed(Instant.now())
                .build());

        mockMvc.perform(get("/analytics/players/{id}/stats", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(10))
                .andExpect(jsonPath("$.totalGames").value(5))
                .andExpect(jsonPath("$.wins").value(3))
                .andExpect(jsonPath("$.winRate").value(60.00))
                .andExpect(jsonPath("$.bestElo").value(1950));
    }

    @Test
    @DisplayName("GET /analytics/players/{id}/stats sobre jugador desconocido responde 404")
    void getPlayerStats_missing_returns404() throws Exception {
        mockMvc.perform(get("/analytics/players/{id}/stats", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /analytics/players/{id}/vs/{op} calcula head-to-head desde game_record")
    void getHeadToHead_persistedGames_returnsAggregatedRecord() throws Exception {
        // Jugador 1 (blanco) gana contra 2 → wins=1
        gameRecordRepo.save(gameRecord(101L, 1L, 2L, "1-0"));
        // Jugador 1 (negro) pierde contra 2 → losses=1
        gameRecordRepo.save(gameRecord(102L, 2L, 1L, "1-0"));
        // Empate
        gameRecordRepo.save(gameRecord(103L, 1L, 2L, "1/2-1/2"));

        mockMvc.perform(get("/analytics/players/{id}/vs/{op}", 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.opponentId").value(2))
                .andExpect(jsonPath("$.totalGames").value(3))
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.losses").value(1))
                .andExpect(jsonPath("$.draws").value(1));
    }

    @Test
    @DisplayName("GET /analytics/players/{id}/vs/{op} sin historial devuelve totales en cero")
    void getHeadToHead_noGames_returnsZeroes() throws Exception {
        mockMvc.perform(get("/analytics/players/{id}/vs/{op}", 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalGames").value(0))
                .andExpect(jsonPath("$.wins").value(0));
    }

    @Test
    @DisplayName("GET /analytics/platform/summary expone agregados de la plataforma")
    void platformSummary_returnsAggregates() throws Exception {
        playerStatsRepo.save(PlayerStatsMV.builder()
                .playerId(1L).totalGames(3).wins(2).losses(1)
                .winRate(new BigDecimal("66.67"))
                .avgMoves(new BigDecimal("35.0"))
                .lastRefreshed(Instant.now()).build());
        gameRecordRepo.save(gameRecord(500L, 1L, 2L, "1-0"));

        mockMvc.perform(get("/analytics/platform/summary"))
                .andExpect(status().isOk());
    }

    private GameRecord gameRecord(long gameId, long white, long black, String result) {
        return GameRecord.builder()
                .gameId(gameId)
                .whitePlayerId(white)
                .blackPlayerId(black)
                .result(result)
                .playedAt(Instant.now())
                .build();
    }
}
