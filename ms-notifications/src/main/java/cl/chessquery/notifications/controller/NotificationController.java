package cl.chessquery.notifications.controller;

import cl.chessquery.notifications.dto.NotificationDto;
import cl.chessquery.notifications.entity.Channel;
import cl.chessquery.notifications.entity.NotificationLog;
import cl.chessquery.notifications.repository.NotificationLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * N1 — Inbox de notificaciones in-app (campana 🔔 de la UI).
 *
 * Devuelve solo notificaciones del canal IN_APP para el destinatario actual.
 * Las notificaciones de email/push se persisten en la misma tabla pero no se
 * exponen aquí.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Inbox de notificaciones in-app por usuario")
public class NotificationController {

    private final NotificationLogRepository repo;

    @Operation(summary = "Lista las últimas N notificaciones in-app del usuario")
    @GetMapping
    public List<NotificationDto> list(
            @RequestParam Long recipientId,
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repo.findByRecipientIdAndChannelOrderByCreatedAtDesc(
                        recipientId, Channel.IN_APP, PageRequest.of(0, safeLimit))
                .stream()
                .map(NotificationDto::from)
                .toList();
    }

    @Operation(summary = "Cantidad de notificaciones no leídas (badge de la campana)")
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@RequestParam Long recipientId) {
        long count = repo.countByRecipientIdAndChannelAndReadAtIsNull(recipientId, Channel.IN_APP);
        return Map.of("count", count);
    }

    @Operation(summary = "Marca una notificación como leída")
    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationDto> markRead(@PathVariable Long id) {
        return repo.findById(id)
                .map(n -> {
                    if (n.getReadAt() == null) {
                        n.setReadAt(Instant.now());
                        repo.save(n);
                    }
                    return ResponseEntity.ok(NotificationDto.from(n));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Marca todas las notificaciones del usuario como leídas")
    @PatchMapping("/read-all")
    @Transactional
    public Map<String, Integer> markAllRead(@RequestParam Long recipientId) {
        List<NotificationLog> unread = repo.findByRecipientIdAndChannelOrderByCreatedAtDesc(
                recipientId, Channel.IN_APP, PageRequest.of(0, 200));
        Instant now = Instant.now();
        int updated = 0;
        for (NotificationLog n : unread) {
            if (n.getReadAt() == null) {
                n.setReadAt(now);
                updated++;
            }
        }
        repo.saveAll(unread);
        return Map.of("updated", updated);
    }
}
