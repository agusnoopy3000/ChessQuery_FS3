package cl.chessquery.notifications.integration;

import cl.chessquery.notifications.entity.Channel;
import cl.chessquery.notifications.entity.NotifStatus;
import cl.chessquery.notifications.entity.NotificationLog;
import cl.chessquery.notifications.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de integración del inbox in-app sobre {@code NotificationController}.
 * Levanta contexto Spring completo con H2 + JPA y valida que listado,
 * contador de no leídas y marcar-como-leída produzcan los cambios esperados
 * tanto en la respuesta HTTP como en la base de datos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Integración HTTP + JPA — NotificationController")
class NotificationIntegrationTest {

    @Autowired MockMvc                  mockMvc;
    @Autowired NotificationLogRepository repo;

    private static final Long ALICE = 100L;
    private static final Long BOB   = 200L;

    @BeforeEach
    void seed() {
        repo.deleteAll();
        repo.save(notif(ALICE, Channel.IN_APP, "Bienvenida", null));
        repo.save(notif(ALICE, Channel.IN_APP, "ELO actualizado", null));
        repo.save(notif(ALICE, Channel.IN_APP, "Inscripción aprobada", Instant.now()));
        repo.save(notif(ALICE, Channel.EMAIL,  "Correo bienvenida", null)); // no debería listarse
        repo.save(notif(BOB,   Channel.IN_APP, "Ruta torneo",       null));
    }

    @Test
    @DisplayName("GET /notifications?recipientId=alice devuelve solo in-app del usuario")
    void list_filtersByRecipientAndInAppChannel() throws Exception {
        mockMvc.perform(get("/notifications").param("recipientId", ALICE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].subject", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not("Correo bienvenida"))));
    }

    @Test
    @DisplayName("GET /notifications/unread-count cuenta solo las in-app sin leer")
    void unreadCount_excludesReadAndOtherUsers() throws Exception {
        mockMvc.perform(get("/notifications/unread-count").param("recipientId", ALICE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("PATCH /notifications/{id}/read marca como leída y persiste readAt")
    void markRead_setsReadAtInDb() throws Exception {
        NotificationLog n = repo.findByRecipientIdAndChannelOrderByCreatedAtDesc(
                ALICE, Channel.IN_APP, org.springframework.data.domain.PageRequest.of(0, 1)).get(0);

        mockMvc.perform(patch("/notifications/{id}/read", n.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        assertThat(repo.findById(n.getId()).orElseThrow().getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("PATCH /notifications/{id}/read sobre id inexistente responde 404")
    void markRead_missingId_returns404() throws Exception {
        mockMvc.perform(patch("/notifications/{id}/read", 9999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /notifications/read-all marca todas las no leídas del usuario")
    void markAllRead_setsReadAtForAllPendingOfUser() throws Exception {
        mockMvc.perform(patch("/notifications/read-all").param("recipientId", ALICE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        long stillUnread = repo.countByRecipientIdAndChannelAndReadAtIsNull(ALICE, Channel.IN_APP);
        assertThat(stillUnread).isZero();

        // No tocó las de otro usuario.
        long bobUnread = repo.countByRecipientIdAndChannelAndReadAtIsNull(BOB, Channel.IN_APP);
        assertThat(bobUnread).isEqualTo(1);
    }

    private NotificationLog notif(Long recipient, Channel channel, String subject, Instant readAt) {
        return NotificationLog.builder()
                .recipientId(recipient)
                .channel(channel)
                .eventType("test.event")
                .status(NotifStatus.SENT)
                .subject(subject)
                .payload("{}")
                .sentAt(Instant.now())
                .readAt(readAt)
                .build();
    }
}
