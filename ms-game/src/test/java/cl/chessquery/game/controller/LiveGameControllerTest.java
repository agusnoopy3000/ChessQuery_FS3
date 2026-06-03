package cl.chessquery.game.controller;

import cl.chessquery.game.dto.LiveGameDtos.LiveGameResponse;
import cl.chessquery.game.service.LiveGameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest de {@link LiveGameController}.
 *
 * <p>Verifica routing y mapping de body para los endpoints de partidas live.</p>
 */
@WebMvcTest(LiveGameController.class)
class LiveGameControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private LiveGameService live;

    private LiveGameResponse sample(Long id, String status) {
        return new LiveGameResponse(id, 1L, 2L, status,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                "w", null, null, 600_000L, 0L, 600_000L, 600_000L, null,
                List.of(), null, null, null, null, null, null);
    }

    @Test
    @DisplayName("create_validBody_returns201")
    void create_validBody_returns201() throws Exception {
        when(live.create(any())).thenReturn(sample(1L, "WAITING"));
        String body = """
                {"whitePlayerId":1,"whiteEloBefore":1500,
                 "timeControlInitialMs":600000,"timeControlIncrementMs":0}""";
        mvc.perform(post("/games/live").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    @DisplayName("get_validId_returns200")
    void get_validId_returns200() throws Exception {
        when(live.get(eq(1L))).thenReturn(sample(1L, "ACTIVE"));
        mvc.perform(get("/games/live/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("join_validBody_returns200")
    void join_validBody_returns200() throws Exception {
        when(live.join(eq(1L), any())).thenReturn(sample(1L, "ACTIVE"));
        mvc.perform(post("/games/live/1/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":2,\"eloBefore\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("move_validBody_returns200")
    void move_validBody_returns200() throws Exception {
        when(live.move(eq(1L), any())).thenReturn(sample(1L, "ACTIVE"));
        mvc.perform(post("/games/live/1/move").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1,\"uci\":\"e2e4\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("resign_validBody_returns200")
    void resign_validBody_returns200() throws Exception {
        when(live.resign(eq(1L), any())).thenReturn(sample(1L, "FINISHED"));
        mvc.perform(post("/games/live/1/resign").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("draw_validBody_returns200")
    void draw_validBody_returns200() throws Exception {
        when(live.drawAgreement(eq(1L), any())).thenReturn(sample(1L, "FINISHED"));
        mvc.perform(post("/games/live/1/draw").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("timeout_validBody_returns200")
    void timeout_validBody_returns200() throws Exception {
        when(live.timeout(eq(1L), any())).thenReturn(sample(1L, "FINISHED"));
        mvc.perform(post("/games/live/1/timeout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rematch_validBody_returns201")
    void rematch_validBody_returns201() throws Exception {
        when(live.rematch(eq(1L), any())).thenReturn(sample(2L, "WAITING"));
        mvc.perform(post("/games/live/1/rematch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("invite_validBody_returnsJsonMap")
    void invite_validBody_returnsJsonMap() throws Exception {
        when(live.invitePlayer(eq(1L), eq("a@b.cl"), any(), any()))
                .thenReturn(Map.of("matched", true, "playerId", 5L));
        mvc.perform(post("/games/live/1/invite")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.cl\",\"gameUrl\":\"https://x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true));
    }
}
