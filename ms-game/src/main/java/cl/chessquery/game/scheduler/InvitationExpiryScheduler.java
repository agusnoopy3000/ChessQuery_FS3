package cl.chessquery.game.scheduler;

import cl.chessquery.game.service.InvitationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barre periódicamente las invitaciones PENDING vencidas y las marca EXPIRED
 * (publicando invite.expired). Complementa la expiración perezosa de
 * {@link InvitationService#get}: garantiza limpieza aunque nadie consulte la
 * invitación (P1-03).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationExpiryScheduler {

    private final InvitationService invitationService;

    @Scheduled(fixedDelayString = "${invitations.expiry-scan-ms:15000}")
    public void expireDueInvitations() {
        try {
            invitationService.expireDue();
        } catch (Exception e) {
            // El barrido no debe tumbar el scheduler: se reintenta al próximo tick.
            log.warn("Fallo al expirar invitaciones vencidas: {}", e.getMessage());
        }
    }
}
