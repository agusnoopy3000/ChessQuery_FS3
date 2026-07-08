package cl.chessquery.game.scheduler;

import cl.chessquery.game.service.InvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationExpirySchedulerTest {

    @Mock private InvitationService invitationService;
    @InjectMocks private InvitationExpiryScheduler scheduler;

    @Test
    @DisplayName("expireDueInvitations_delegaEnElServicio")
    void delegatesToService() {
        when(invitationService.expireDue()).thenReturn(3);
        scheduler.expireDueInvitations();
        verify(invitationService).expireDue();
    }

    @Test
    @DisplayName("expireDueInvitations_siElServicioFalla_noPropagaExcepcion")
    void swallowsExceptions() {
        when(invitationService.expireDue()).thenThrow(new RuntimeException("db down"));
        assertThatNoException().isThrownBy(() -> scheduler.expireDueInvitations());
    }
}
