package cl.chessquery.game.controller;

import cl.chessquery.game.dto.GameResponse;
import cl.chessquery.game.dto.PageResponse;
import cl.chessquery.game.dto.PgnUrlResponse;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.service.GameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest de {@link GameController}.
 *
 * <p>Verifica routing, deserialización del body y serialización del response,
 * más manejo de errores 404 vía {@link ApiException}.</p>
 */
@WebMvcTest(GameController.class)
class GameControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private GameService gameService;

    private GameResponse sample(Long id) {
        return new GameResponse(id, 1L, 2L, "1-0", "CASUAL", 1500, 1500, 1516, 1484, 10,
                null, null, null, null, 60, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("registerGame_validBody_returns201")
    void registerGame_validBody_returns201() throws Exception {
        when(gameService.registerGame(any())).thenReturn(sample(1L));

        String body = """
                {"whitePlayerId":1,"blackPlayerId":2,"result":"1-0","gameType":"CASUAL",
                 "whiteEloBefore":1500,"blackEloBefore":1500,"totalMoves":10,
                 "tournamentPairingId":null,"durationSeconds":60,"playedAt":null,"pgnContent":null}
                """;
        mvc.perform(post("/games").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("getGame_existingId_returns200")
    void getGame_existingId_returns200() throws Exception {
        when(gameService.getGame(eq(7L))).thenReturn(sample(7L));
        mvc.perform(get("/games/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("listGames_withPlayerFilter_returnsPage")
    void listGames_withPlayerFilter_returnsPage() throws Exception {
        when(gameService.listGames(eq(1L), eq(GameType.CASUAL), eq("1-0"), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(sample(1L)), 0, 20, 1L, 1));
        mvc.perform(get("/games").param("playerId", "1").param("gameType", "CASUAL").param("result", "1-0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("getPgnUrl_validId_returnsPresignedUrl")
    void getPgnUrl_validId_returnsPresignedUrl() throws Exception {
        when(gameService.getPgnUrl(eq(5L)))
                .thenReturn(new PgnUrlResponse("https://signed", Instant.now()));
        mvc.perform(get("/games/5/pgn-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://signed"));
    }
}
