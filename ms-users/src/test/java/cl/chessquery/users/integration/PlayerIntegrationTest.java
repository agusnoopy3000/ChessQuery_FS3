package cl.chessquery.users.integration;

import cl.chessquery.users.dto.UpdateEloRequest;
import cl.chessquery.users.dto.UpdateProfileRequest;
import cl.chessquery.users.entity.Player;
import cl.chessquery.users.entity.RatingType;
import cl.chessquery.users.repository.PlayerRepository;
import cl.chessquery.users.service.EventPublisherService;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración HTTP→Service→JPA→H2 sobre {@code UserController}.
 * <p>
 * A diferencia de {@link cl.chessquery.users.controller.UserControllerIntegrationTest}
 * que es un slice {@code @WebMvcTest}, este test levanta el contexto completo
 * (perfil {@code test}: H2 en memoria + JPA {@code create-drop} + RabbitMQ
 * deshabilitado) y valida que las peticiones HTTP reales persistan datos en
 * la base. Solo se mockean las dependencias externas no determinísticas
 * ({@link EventPublisherService}) para que las aserciones se enfoquen en el
 * resultado observable vía API + base de datos.
 *
 * <p>Patrón Given-When-Then en cada caso para reflejar claramente el flujo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integración HTTP + JPA — Player")
class PlayerIntegrationTest {

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     mapper;
    @Autowired PlayerRepository playerRepository;

    /** No queremos publicar a Rabbit en tests; el bean real depende del template. */
    @MockBean EventPublisherService eventPublisherService;

    private Player seededPlayer;

    @BeforeEach
    void seedPlayer() {
        playerRepository.deleteAll();
        seededPlayer = playerRepository.save(Player.builder()
                .firstName("Rodrigo")
                .lastName("Sepúlveda")
                .email("rodrigo@demo.cl")
                .supabaseUserId(UUID.fromString("11111111-2222-3333-4444-555555555555"))
                .region("Metropolitana")
                .eloNational(2100)
                .eloFideStandard(2050)
                .build());
    }

    // ── GET /users/{id}/profile ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{id}/profile devuelve 200 con perfil persistido")
    void getProfile_existingPlayer_returnsProfile() throws Exception {
        mockMvc.perform(get("/users/{id}/profile", seededPlayer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seededPlayer.getId()))
                .andExpect(jsonPath("$.firstName").value("Rodrigo"))
                .andExpect(jsonPath("$.lastName").value("Sepúlveda"))
                .andExpect(jsonPath("$.eloNational").value(2100))
                .andExpect(jsonPath("$.email").value("rodrigo@demo.cl"));
    }

    @Test
    @DisplayName("GET /users/{id}/profile devuelve 404 cuando el id no existe")
    void getProfile_missingId_returns404() throws Exception {
        mockMvc.perform(get("/users/{id}/profile", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLAYER_NOT_FOUND"));
    }

    // ── GET /users/by-supabase-id/{uuid} ──────────────────────────────────────

    @Test
    @DisplayName("GET /users/by-supabase-id/{uuid} resuelve el Player por UUID Supabase")
    void getBySupabaseId_existingUuid_returnsProfile() throws Exception {
        mockMvc.perform(get("/users/by-supabase-id/{uuid}",
                        "11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seededPlayer.getId()))
                .andExpect(jsonPath("$.firstName").value("Rodrigo"));
    }

    @Test
    @DisplayName("GET /users/by-supabase-id/{uuid} responde 404 si el UUID no está mapeado")
    void getBySupabaseId_unknownUuid_returns404() throws Exception {
        mockMvc.perform(get("/users/by-supabase-id/{uuid}",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLAYER_NOT_FOUND"));
    }

    // ── PUT /users/{id}/profile ───────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id}/profile actualiza nombre y región y persiste en H2")
    void updateProfile_validRequest_persistsChanges() throws Exception {
        UpdateProfileRequest req = new UpdateProfileRequest(
                "Carlos", "López", null, "Valparaíso", null);

        mockMvc.perform(put("/users/{id}/profile", seededPlayer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Carlos"))
                .andExpect(jsonPath("$.region").value("Valparaíso"));

        // El cambio quedó realmente persistido en la BD.
        Player reloaded = playerRepository.findById(seededPlayer.getId()).orElseThrow();
        assertThat(reloaded.getFirstName()).isEqualTo("Carlos");
        assertThat(reloaded.getLastName()).isEqualTo("López");
        assertThat(reloaded.getRegion()).isEqualTo("Valparaíso");
    }

    // ── PUT /users/{id}/elo ───────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /users/{id}/elo actualiza el ELO nacional y devuelve 204")
    void updateElo_national_returns204AndPersists() throws Exception {
        UpdateEloRequest req = new UpdateEloRequest(RatingType.NATIONAL, 2150, "FIDE");

        mockMvc.perform(put("/users/{id}/elo", seededPlayer.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        Player reloaded = playerRepository.findById(seededPlayer.getId()).orElseThrow();
        assertThat(reloaded.getEloNational()).isEqualTo(2150);
    }

    @Test
    @DisplayName("PUT /users/{id}/elo sobre Player inexistente responde 404")
    void updateElo_missingPlayer_returns404() throws Exception {
        UpdateEloRequest req = new UpdateEloRequest(RatingType.FIDE_STANDARD, 2200, "FIDE");

        mockMvc.perform(put("/users/{id}/elo", 9999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── GET /users (listado/conteo) ───────────────────────────────────────────

    @Test
    @DisplayName("GET /users devuelve el total real persistido")
    void list_returnsRealTotalElements() throws Exception {
        playerRepository.save(Player.builder()
                .firstName("Ana").lastName("Vega").email("ana@demo.cl").build());

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
