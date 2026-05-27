package cl.chessquery.notifications.controller;

import cl.chessquery.notifications.entity.Channel;
import cl.chessquery.notifications.entity.NotifStatus;
import cl.chessquery.notifications.entity.NotificationLog;
import cl.chessquery.notifications.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest de {@link NotificationController}.
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean NotificationLogRepository repo;

    private NotificationLog log(long id, boolean read) {
        return NotificationLog.builder()
                .id(id).recipientId(1L).channel(Channel.IN_APP)
                .eventType("user.registered").status(NotifStatus.SENT)
                .subject("Hola").payload("{}").sentAt(Instant.now())
                .readAt(read ? Instant.now() : null)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("list_returns200WithNotifications")
    void list_returns200WithNotifications() throws Exception {
        when(repo.findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(1L), eq(Channel.IN_APP), any(Pageable.class)))
                .thenReturn(List.of(log(1L, false)));
        mvc.perform(get("/notifications").param("recipientId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("unreadCount_returnsCount")
    void unreadCount_returnsCount() throws Exception {
        when(repo.countByRecipientIdAndChannelAndReadAtIsNull(eq(1L), eq(Channel.IN_APP)))
                .thenReturn(3L);
        mvc.perform(get("/notifications/unread-count").param("recipientId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("markRead_existing_returnsDto")
    void markRead_existing_returnsDto() throws Exception {
        when(repo.findById(eq(1L))).thenReturn(Optional.of(log(1L, false)));
        when(repo.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(patch("/notifications/1/read")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("markRead_notFound_returns404")
    void markRead_notFound_returns404() throws Exception {
        when(repo.findById(eq(99L))).thenReturn(Optional.empty());
        mvc.perform(patch("/notifications/99/read")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("markAllRead_returnsUpdatedCount")
    void markAllRead_returnsUpdatedCount() throws Exception {
        when(repo.findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(1L), eq(Channel.IN_APP), any(Pageable.class)))
                .thenReturn(List.of(log(1L, false), log(2L, false), log(3L, true)));
        mvc.perform(patch("/notifications/read-all").param("recipientId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));
    }
}
