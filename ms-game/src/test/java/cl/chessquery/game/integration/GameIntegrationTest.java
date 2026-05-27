package cl.chessquery.game.integration;

import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.entity.Game;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.repository.GameRepository;
import cl.chessquery.game.service.EventPublisherService;
import cl.chessquery.game.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración del registro y consulta de partidas sobre
 * {@code GameController}. Levanta el contexto Spring completo con H2 y JPA
 * {@code create-drop}; las dependencias externas (Supabase Storage y
 * publicación a Rabbit) se mockean para concentrar las aserciones en el
 * flujo HTTP → Service → Repository y la persistencia efectiva.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "supabase.url=http://localhost",
                "supabase.service-key=test-key"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integración HTTP + JPA — Game")
class GameIntegrationTest {

    @Autowired MockMvc        mockMvc;
    @Autowired ObjectMapper   mapper;
    @Autowired GameRepository gameRepository;

    @MockBean EventPublisherService eventPublisherService;
    @MockBean StorageService        storageService;

    @BeforeEach
    void cleanDb() {
        gameRepository.deleteAll();
    }

    // ── POST /games (sin PGN para evitar opening + storage real) ──────────────

    @Test
    @DisplayName("POST /games registra la partida con ELO calculado y devuelve 201")
    void registerGame_validRequest_returns201AndPersists() throws Exception {
        RegisterGameRequest req = new RegisterGameRequest(
                10L, 20L, "1-0",
                GameType.CASUAL,
                1800, 1750,
                40, null, null, Instant.now(), null);

        mockMvc.perform(post("/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.result").value("1-0"))
                .andExpect(jsonPath("$.whitePlayerId").value(10))
                .andExpect(jsonPath("$.blackPlayerId").value(20))
                .andExpect(jsonPath("$.whiteEloAfter").value(org.hamcrest.Matchers.greaterThan(1800)))
                .andExpect(jsonPath("$.blackEloAfter").value(org.hamcrest.Matchers.lessThan(1750)));

        assertThat(gameRepository.findAll()).hasSize(1);
        Game saved = gameRepository.findAll().get(0);
        assertThat(saved.getWhitePlayerId()).isEqualTo(10L);
        assertThat(saved.getResult()).isEqualTo("1-0");
        assertThat(saved.getWhiteEloAfter()).isNotNull();
        assertThat(saved.getBlackEloAfter()).isNotNull();
    }

    // ── GET /games/{id} ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /games/{id} devuelve la partida persistida")
    void getById_existingGame_returnsResponse() throws Exception {
        Game g = gameRepository.save(seedGame(11L, 22L, "0-1", 1700, 1800));

        mockMvc.perform(get("/games/{id}", g.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(g.getId()))
                .andExpect(jsonPath("$.result").value("0-1"))
                .andExpect(jsonPath("$.whitePlayerId").value(11));
    }

    @Test
    @DisplayName("GET /games/{id} responde 404 cuando no existe")
    void getById_missing_returns404() throws Exception {
        mockMvc.perform(get("/games/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    // ── GET /games (lista paginada) ───────────────────────────────────────────

    @Test
    @DisplayName("GET /games?playerId={id} filtra historial del jugador")
    void list_filterByPlayer_returnsOnlyTheirGames() throws Exception {
        gameRepository.save(seedGame(1L, 2L, "1-0", 1500, 1500));
        gameRepository.save(seedGame(2L, 3L, "1/2-1/2", 1500, 1500));
        gameRepository.save(seedGame(4L, 5L, "0-1", 1500, 1500));

        mockMvc.perform(get("/games").param("playerId", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].whitePlayerId").value(1));
    }

    @Test
    @DisplayName("GET /games sin filtros devuelve el total real")
    void list_noFilter_returnsAll() throws Exception {
        gameRepository.save(seedGame(1L, 2L, "1-0", 1500, 1500));
        gameRepository.save(seedGame(2L, 3L, "1/2-1/2", 1500, 1500));

        mockMvc.perform(get("/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    private Game seedGame(long white, long black, String result,
                          int whiteElo, int blackElo) {
        return Game.builder()
                .whitePlayerId(white)
                .blackPlayerId(black)
                .result(result)
                .gameType(GameType.CASUAL)
                .whiteEloBefore(whiteElo)
                .blackEloBefore(blackElo)
                .whiteEloAfter(whiteElo)
                .blackEloAfter(blackElo)
                .totalMoves(30)
                .playedAt(Instant.now())
                .build();
    }
}
