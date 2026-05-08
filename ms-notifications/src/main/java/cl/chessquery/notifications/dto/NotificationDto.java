package cl.chessquery.notifications.dto;

import cl.chessquery.notifications.entity.NotificationLog;

import java.time.Instant;

public record NotificationDto(
        Long id,
        Long recipientId,
        String eventType,
        String subject,
        String payload,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationDto from(NotificationLog log) {
        return new NotificationDto(
                log.getId(),
                log.getRecipientId(),
                log.getEventType(),
                log.getSubject(),
                log.getPayload(),
                log.getCreatedAt(),
                log.getReadAt()
        );
    }
}
