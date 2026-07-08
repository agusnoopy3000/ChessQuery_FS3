package cl.chessquery.game.repository;

import cl.chessquery.game.entity.GameInvitation;
import cl.chessquery.game.entity.GameInvitation.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface GameInvitationRepository extends JpaRepository<GameInvitation, Long> {

    /** Invitaciones vencidas aún en PENDING — usado por el scheduler de expiración. */
    List<GameInvitation> findByStatusAndExpiresAtBefore(InvitationStatus status, Instant cutoff);

    List<GameInvitation> findBySessionId(Long sessionId);
}
