package cl.chessquery.users.controller;

import cl.chessquery.users.dto.*;
import cl.chessquery.users.exception.ApiException;
import cl.chessquery.users.service.PlayerService;
import cl.chessquery.users.service.RankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test de la capa web (controller + exception handler).
 * Usa @WebMvcTest para aislar la capa HTTP sin necesitar BD ni RabbitMQ.
 */
@WebMvcTest(UserController.class)
class UserControllerIntegrationTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean PlayerService  playerService;
    @MockBean RankingService rankingService;

    // ── GET /users/{id}/profile ───────────────────────────────────────────────

    @Test
    void getProfile_existingPlayer_returns200() throws Exception {
        PlayerProfileResponse profile = sampleProfile(4L, "Rodrigo", "Sepúlveda");
        when(playerService.getProfile(4L)).thenReturn(profile);

        mockMvc.perform(get("/users/4/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.firstName").value("Rodrigo"))
                .andExpect(jsonPath("$.lastName").value("Sepúlveda"))
                .andExpect(jsonPath("$.eloNational").value(2100));
    }

    @Test
    void getProfile_notFound_returns404() throws Exception {
        when(playerService.getProfile(999L))
                .thenThrow(new ApiException(404, "PLAYER_NOT_FOUND", "Jugador no encontrado"));

        mockMvc.perform(get("/users/999/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLAYER_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /users/search ─────────────────────────────────────────────────────

    @Test
    void search_validQuery_returns200WithList() throws Exception {
        when(playerService.search("Rodrigo", 20)).thenReturn(List.of(
                new PlayerSearchResponse(4L, "Rodrigo", "Sepúlveda",
                        "3600001", "15234567-8", "CHL", 2100, 2050, "FM")
        ));

        mockMvc.perform(get("/users/search").param("q", "Rodrigo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("Rodrigo"))
                .andExpect(jsonPath("$[0].currentTitle").value("FM"));
    }

    @Test
    void search_emptyQuery_returns400() throws Exception {
        when(playerService.search(eq(""), anyInt()))
                .thenThrow(new ApiException(400, "INVALID_QUERY", "Query vacío"));

        mockMvc.perform(get("/users/search").param("q", ""))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /users/{id}/profile ───────────────────────────────────────────────

    @Test
    void updateProfile_validRequest_returns200() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest("Carlos", "López", null, "Valparaíso");
        PlayerProfileResponse updated = sampleProfile(4L, "Carlos", "López");
        when(playerService.updateProfile(eq(4L), any())).thenReturn(updated);

        mockMvc.perform(put("/users/4/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Carlos"));
    }

    // ── GET /users/ranking ────────────────────────────────────────────────────

    @Test
    void ranking_returnsOrderedList() throws Exception {
        when(rankingService.getRanking(null, null, 50)).thenReturn(List.of(
                new RankingEntryResponse(1, 4L, "Rodrigo", "Sepúlveda",
                        "Metropolitana", "Lasker", 2100, 2050, "FM", "ADULTO"),
                new RankingEntryResponse(2, 8L, "Ignacio", "Pérez",
                        "Metropolitana", "UCH", 2050, 2010, "FM", "ADULTO")
        ));

        mockMvc.perform(get("/users/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].position").value(1))
                .andExpect(jsonPath("$[0].eloNational").value(2100));
    }

    // ── PUT /users/{id}/elo ───────────────────────────────────────────────────

    @Test
    void updateElo_validRequest_returns204() throws Exception {
        UpdateEloRequest req = new UpdateEloRequest(
                cl.chessquery.users.entity.RatingType.NATIONAL, 2150, "FIDE");

        mockMvc.perform(put("/users/4/elo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PlayerProfileResponse sampleProfile(Long id, String first, String last) {
        return new PlayerProfileResponse(
                id, first, last, first.toLowerCase() + "@demo.cl",
                "15234567-8", LocalDate.of(1992, 3, 15), "M",
                "Metropolitana", "3600001", "rodrigo_chess",
                new CountryDto(1, "CHL", "Chile", "CHI"),
                new ClubDto(1, "Club de Ajedrez Lasker", "Santiago", "CL-LAS"),
                2100, 2050, null, null, null, "FM",
                Instant.now(), Instant.now()
        );
    }
}
