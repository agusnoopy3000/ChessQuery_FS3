package cl.chessquery.tournament.integration;

import cl.chessquery.tournament.client.UserEloClient;
import cl.chessquery.tournament.dto.CreateTournamentRequest;
import cl.chessquery.tournament.dto.StatusTransitionRequest;
import cl.chessquery.tournament.entity.Tournament;
import cl.chessquery.tournament.entity.TournamentFormat;
import cl.chessquery.tournament.entity.TournamentStatus;
import cl.chessquery.tournament.repository.TournamentRepository;
import cl.chessquery.tournament.service.EventPublisherService;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración para {@code TournamentController}.
 * <p>
 * Levanta el contexto Spring completo con H2 + JPA {@code create-drop} y
 * verifica el flujo HTTP → Service → Repository, asegurando que las
 * operaciones realmente persistan torneos. Las dependencias externas
 * ({@link EventPublisherService}, {@link UserEloClient}) se mockean para que
 * los tests no requieran broker ni red.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integración HTTP + JPA — Tournament")
class TournamentIntegrationTest {

    @Autowired MockMvc              mockMvc;
    @Autowired ObjectMapper         mapper;
    @Autowired TournamentRepository tournamentRepository;

    @MockBean EventPublisherService eventPublisherService;
    @MockBean UserEloClient         userEloClient;

    @BeforeEach
    void cleanDb() {
        tournamentRepository.deleteAll();
    }

    // ── POST /tournaments ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /tournaments crea el torneo y lo persiste en H2")
    void create_validRequest_persistsTournament() throws Exception {
        CreateTournamentRequest req = new CreateTournamentRequest(
                "Magistral 2026", "Torneo magistral por invitación",
                TournamentFormat.SWISS,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 7),
                "Santiago", 16, 7, 42L,
                1800, 2400, "90+30", true);

        mockMvc.perform(post("/tournaments")
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Magistral 2026"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.organizerId").value(42));

        assertThat(tournamentRepository.findAll()).hasSize(1);
        Tournament saved = tournamentRepository.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("Magistral 2026");
        assertThat(saved.getFormat()).isEqualTo(TournamentFormat.SWISS);
        assertThat(saved.getStatus()).isEqualTo(TournamentStatus.DRAFT);
    }

    @Test
    @DisplayName("POST /tournaments sin rol ORGANIZER devuelve 403")
    void create_withoutOrganizerRole_returns403() throws Exception {
        CreateTournamentRequest req = new CreateTournamentRequest(
                "Open", null, TournamentFormat.SWISS, null, null, null,
                null, null, 1L, null, null, null, null);

        mockMvc.perform(post("/tournaments")
                        .header("X-User-Role", "PLAYER")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertThat(tournamentRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /tournaments rechaza payload inválido (nombre vacío) con 400")
    void create_emptyName_returns400() throws Exception {
        String body = "{\"name\":\"\",\"format\":\"SWISS\",\"organizerId\":1}";

        mockMvc.perform(post("/tournaments")
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── GET /tournaments/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /tournaments/{id} devuelve el torneo persistido")
    void getById_existingTournament_returnsResponse() throws Exception {
        Tournament t = tournamentRepository.save(seed("Open Verano", TournamentStatus.OPEN, 42L));

        mockMvc.perform(get("/tournaments/{id}", t.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(t.getId()))
                .andExpect(jsonPath("$.name").value("Open Verano"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("GET /tournaments/{id} responde 404 si el id no existe")
    void getById_missing_returns404() throws Exception {
        mockMvc.perform(get("/tournaments/{id}", 9999L))
                .andExpect(status().isNotFound());
    }

    // ── GET /tournaments (lista paginada) ─────────────────────────────────────

    @Test
    @DisplayName("GET /tournaments lista los torneos persistidos")
    void list_returnsPersistedTournaments() throws Exception {
        tournamentRepository.save(seed("Open A", TournamentStatus.OPEN, 1L));
        tournamentRepository.save(seed("Open B", TournamentStatus.DRAFT, 1L));
        tournamentRepository.save(seed("Open C", TournamentStatus.FINISHED, 1L));

        mockMvc.perform(get("/tournaments").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /tournaments?status=OPEN filtra por estado")
    void list_filterByStatus_returnsOnlyMatching() throws Exception {
        tournamentRepository.save(seed("A", TournamentStatus.OPEN, 1L));
        tournamentRepository.save(seed("B", TournamentStatus.DRAFT, 1L));

        mockMvc.perform(get("/tournaments").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("A"));
    }

    // ── PATCH /tournaments/{id}/status ────────────────────────────────────────

    @Test
    @DisplayName("PATCH /tournaments/{id}/status transiciona DRAFT → OPEN")
    void transition_draftToOpen_persistsChange() throws Exception {
        Tournament t = tournamentRepository.save(Tournament.builder()
                .name("Magistral")
                .format(TournamentFormat.SWISS)
                .status(TournamentStatus.DRAFT)
                .organizerId(42L)
                .roundsTotal(7)
                .requiresApproval(true)
                .build());
        StatusTransitionRequest req = new StatusTransitionRequest(TournamentStatus.OPEN);

        mockMvc.perform(patch("/tournaments/{id}/status", t.getId())
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        Tournament reloaded = tournamentRepository.findById(t.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TournamentStatus.OPEN);
    }

    // ── DELETE /tournaments/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /tournaments/{id} elimina torneo en DRAFT del owner")
    void delete_draftOwnedByOrganizer_returns204() throws Exception {
        Tournament t = tournamentRepository.save(seed("Borrador", TournamentStatus.DRAFT, 42L));

        mockMvc.perform(delete("/tournaments/{id}", t.getId())
                        .header("X-User-Role", "ORGANIZER")
                        .header("X-User-Id", 42L))
                .andExpect(status().isNoContent());

        assertThat(tournamentRepository.findById(t.getId())).isEmpty();
    }

    private Tournament seed(String name, TournamentStatus status, Long organizerId) {
        return Tournament.builder()
                .name(name)
                .format(TournamentFormat.SWISS)
                .status(status)
                .organizerId(organizerId)
                .requiresApproval(true)
                .build();
    }
}
