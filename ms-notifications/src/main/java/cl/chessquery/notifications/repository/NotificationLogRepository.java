package cl.chessquery.notifications.repository;

import cl.chessquery.notifications.entity.Channel;
import cl.chessquery.notifications.entity.NotificationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByRecipientIdAndChannelOrderByCreatedAtDesc(
            Long recipientId, Channel channel, Pageable pageable);

    long countByRecipientIdAndChannelAndReadAtIsNull(Long recipientId, Channel channel);
}
