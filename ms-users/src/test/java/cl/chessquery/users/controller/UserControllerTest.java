package cl.chessquery.users.controller;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.service.PlayerService;
import cl.chessquery.users.service.RankingService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest de {@link UserController}.
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private PlayerService playerService;
    @MockBean private RankingService rankingService;
    @MockBean private PlayerRepository playerRepository;

    private PlayerProfileResponse sample(long id) {
        return new PlayerProfileResponse(id, "A", "B", "a@b.cl", null, null, null, null,
                null, null, null, null, null, null, null, 1500, null, null, null, null,
                null, null, null, null, null, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("list_returnsPagination")
    void list_returnsPagination() throws Exception {
        when(playerRepository.count()).thenReturn(42L);
        mvc.perform(get("/users").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(42));
    }

    @Test
    @DisplayName("syncFromAuth_returns200")
    void syncFromAuth_returns200() throws Exception {
        when(playerService.syncFromAuth(any())).thenReturn(sample(1L));
        mvc.perform(post("/users/sync").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"email\":\"a@b.cl\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getProfile_returns200")
    void getProfile_returns200() throws Exception {
        when(playerService.getProfile(eq(1L))).thenReturn(sample(1L));
        mvc.perform(get("/users/1/profile")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("getBySupabaseId_returns200")
    void getBySupabaseId_returns200() throws Exception {
        when(playerService.getProfileBySupabaseId(any())).thenReturn(sample(1L));
        mvc.perform(get("/users/by-supabase-id/550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getByEmail_returns200")
    void getByEmail_returns200() throws Exception {
        when(playerService.getProfileByEmail(eq("a@b.cl"))).thenReturn(sample(1L));
        mvc.perform(get("/users/by-email").param("email", "a@b.cl"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("provision_returns200")
    void provision_returns200() throws Exception {
        when(playerService.provisionBySupabaseId(any())).thenReturn(sample(1L));
        mvc.perform(post("/users/provision").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supabaseUserId\":\"550e8400-e29b-41d4-a716-446655440000\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateProfile_returns200")
    void updateProfile_returns200() throws Exception {
        when(playerService.updateProfile(eq(1L), any())).thenReturn(sample(1L));
        mvc.perform(put("/users/1/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"X\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("search_returns200")
    void search_returns200() throws Exception {
        when(playerService.search(eq("ana"), eq(20))).thenReturn(List.of());
        mvc.perform(get("/users/search").param("q", "ana")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ratingHistory_returns200")
    void ratingHistory_returns200() throws Exception {
        when(playerService.getRatingHistory(eq(1L), eq(RatingType.NATIONAL)))
                .thenReturn(List.of());
        mvc.perform(get("/users/1/rating-history")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ranking_returns200")
    void ranking_returns200() throws Exception {
        when(rankingService.getRanking(any(), any(), eq(50))).thenReturn(List.of());
        mvc.perform(get("/users/ranking")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("updateElo_returns204")
    void updateElo_returns204() throws Exception {
        mvc.perform(put("/users/1/elo").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"NATIONAL\",\"newValue\":1500,\"source\":\"ETL\"}"))
                .andExpect(status().isNoContent());
    }
}
