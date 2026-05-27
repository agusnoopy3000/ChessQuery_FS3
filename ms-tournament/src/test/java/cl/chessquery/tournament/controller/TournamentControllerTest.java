package cl.chessquery.tournament.controller;

import cl.chessquery.tournament.dto.*;
import cl.chessquery.tournament.entity.TournamentFormat;
import cl.chessquery.tournament.entity.TournamentStatus;
import cl.chessquery.tournament.service.TournamentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest de {@link TournamentController}.
 *
 * <p>Verifica routing y enforcement de headers X-User-Role/X-User-Id para
 * operaciones que requieren ORGANIZER o ADMIN.</p>
 */
@WebMvcTest(TournamentController.class)
class TournamentControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private TournamentService service;

    private TournamentResponse sample(Long id) {
        return new TournamentResponse(id, "Open", "d", "SWISS", "DRAFT",
                LocalDate.now(), null, "Online", 16, 5, 9L, 1000, 2000, "10+0", true,
                0, 0, Instant.now());
    }

    @Test
    @DisplayName("createTournament_withoutRole_returns403")
    void createTournament_withoutRole_returns403() throws Exception {
        String body = """
                {"name":"X","format":"SWISS","startDate":"2026-06-01","roundsTotal":5,
                 "requiresApproval":true}""";
        mvc.perform(post("/tournaments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("createTournament_organizerRole_returns201")
    void createTournament_organizerRole_returns201() throws Exception {
        when(service.createTournament(any(), eq(9L))).thenReturn(sample(1L));
        String body = """
                {"name":"X","format":"SWISS","startDate":"2026-06-01","roundsTotal":5,
                 "requiresApproval":true}""";
        mvc.perform(post("/tournaments")
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 9L)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("createTournament_missingOrganizerId_returns400")
    void createTournament_missingOrganizerId_returns400() throws Exception {
        String body = """
                {"name":"X","format":"SWISS","startDate":"2026-06-01","roundsTotal":5,
                 "requiresApproval":true}""";
        mvc.perform(post("/tournaments")
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("listTournaments_returnsPage")
    void listTournaments_returnsPage() throws Exception {
        when(service.listTournaments(eq(TournamentStatus.OPEN), eq(TournamentFormat.SWISS), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(sample(1L)), 0, 20, 1L, 1));
        mvc.perform(get("/tournaments").param("status", "OPEN").param("format", "SWISS"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getTournament_existing_returns200")
    void getTournament_existing_returns200() throws Exception {
        when(service.getTournament(eq(1L))).thenReturn(sample(1L));
        mvc.perform(get("/tournaments/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("deleteTournament_withoutRole_returns403")
    void deleteTournament_withoutRole_returns403() throws Exception {
        mvc.perform(delete("/tournaments/1")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deleteTournament_organizer_returns204")
    void deleteTournament_organizer_returns204() throws Exception {
        mvc.perform(delete("/tournaments/1")
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 9L))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("transitionStatus_organizer_returns200")
    void transitionStatus_organizer_returns200() throws Exception {
        when(service.transitionStatus(eq(1L), eq(TournamentStatus.OPEN))).thenReturn(sample(1L));
        mvc.perform(patch("/tournaments/1/status")
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"OPEN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("transitionStatus_withoutRole_returns403")
    void transitionStatus_withoutRole_returns403() throws Exception {
        mvc.perform(patch("/tournaments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\":\"OPEN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("joinTournament_returns201")
    void joinTournament_returns201() throws Exception {
        when(service.joinTournament(eq(1L), eq(5L)))
                .thenReturn(new RegistrationResponse(1L, 1L, 5L, "PENDING", 1500, Instant.now()));
        mvc.perform(post("/tournaments/1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":5}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("approveRegistration_organizer_returns200")
    void approveRegistration_organizer_returns200() throws Exception {
        when(service.approveRegistration(eq(1L)))
                .thenReturn(new RegistrationResponse(1L, 1L, 5L, "CONFIRMED", 1500, Instant.now()));
        mvc.perform(patch("/tournaments/registrations/1/approve")
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("approveRegistration_withoutRole_returns403")
    void approveRegistration_withoutRole_returns403() throws Exception {
        mvc.perform(patch("/tournaments/registrations/1/approve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("rejectRegistration_organizer_returns200")
    void rejectRegistration_organizer_returns200() throws Exception {
        when(service.rejectRegistration(eq(1L), any()))
                .thenReturn(new RegistrationResponse(1L, 1L, 5L, "REJECTED", 1500, Instant.now()));
        mvc.perform(patch("/tournaments/registrations/1/reject")
                        .header("X-User-Role", "ORGANIZER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"bad\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("generateRound_organizer_returns201")
    void generateRound_organizer_returns201() throws Exception {
        when(service.generateRound(eq(1L), eq(1)))
                .thenReturn(new RoundResponse(1L, 1L, 1, null, "IN_PROGRESS", List.of()));
        mvc.perform(post("/tournaments/1/rounds/1")
                        .header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("generateRound_withoutRole_returns403")
    void generateRound_withoutRole_returns403() throws Exception {
        mvc.perform(post("/tournaments/1/rounds/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getRound_returns200")
    void getRound_returns200() throws Exception {
        when(service.getRound(eq(1L), eq(1)))
                .thenReturn(new RoundResponse(1L, 1L, 1, null, "IN_PROGRESS", List.of()));
        mvc.perform(get("/tournaments/1/rounds/1")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("recordResult_returns200")
    void recordResult_returns200() throws Exception {
        when(service.recordResult(eq(1L), eq("1-0")))
                .thenReturn(new PairingResponse(1L, 7L, 1L, 2L, "1-0", 1));
        mvc.perform(patch("/tournaments/pairings/1/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"1-0\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getStandings_returns200")
    void getStandings_returns200() throws Exception {
        when(service.getStandings(eq(1L))).thenReturn(List.of());
        mvc.perform(get("/tournaments/1/standings")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("listRegistrations_returns200")
    void listRegistrations_returns200() throws Exception {
        when(service.listRegistrations(eq(1L))).thenReturn(List.of());
        mvc.perform(get("/tournaments/1/registrations")).andExpect(status().isOk());
    }
}
