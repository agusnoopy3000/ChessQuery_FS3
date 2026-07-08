package cl.chessquery.game.service;

import cl.chessquery.game.entity.GameInvitation;
import cl.chessquery.game.entity.GameInvitation.InvitationStatus;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.repository.GameInvitationRepository;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link InvitationService} — la máquina de estados de
 * invitaciones con TTL (P1-03). Mockea repos y {@link EventPublisherService}.
 */
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private GameInvitationRepository invRepo;
    @Mock private LiveGameSessionRepository sessionRepo;
    @Mock private EventPublisherService events;

    @InjectMocks private InvitationService service;

    private LiveGameSession waitingSession() {
        LiveGameSession s = LiveGameSession.builder()
                .whitePlayerId(1L)
                .status(SessionStatus.WAITING)
                .timeControlInitialMs(180_000L)
                .timeControlIncrementMs(2_000L)
                .build();
        s.setId(50L);
        return s;
    }

    private void stubSaveEchoesWithId() {
        when(invRepo.save(any())).thenAnswer(a -> {
            GameInvitation g = a.getArgument(0);
            if (g.getId() == null) g.setId(77L);
            return g;
        });
    }

    private GameInvitation pending(Long toPlayerId, Instant expiresAt) {
        return GameInvitation.builder()
                .id(77L).sessionId(50L).fromPlayerId(1L)
                .toPlayerId(toPlayerId).toEmail("rival@mail.cl")
                .inviteeColor("b").status(InvitationStatus.PENDING)
                .expiresAt(expiresAt).createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("create_sesionWaiting_persistePendingYPublicaInviteCreated")
        void create_happy() {
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(waitingSession()));
            stubSaveEchoesWithId();

            GameInvitation inv = service.create(50L, 1L, 2L, "rival@mail.cl", "w", 90L);

            assertThat(inv.getStatus()).isEqualTo(InvitationStatus.PENDING);
            assertThat(inv.getInviteeColor()).isEqualTo("w");
            assertThat(inv.getTimeControlInitialMs()).isEqualTo(180_000L);
            verify(events).publishInviteCreated(eq(77L), eq(50L), eq(1L), eq(2L),
                    eq(180_000L), eq(2_000L), eq("w"), any(Instant.class), eq(90L));
        }

        @Test
        @DisplayName("create_sesionInexistente_lanza404")
        void create_sessionNotFound() {
            when(sessionRepo.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.create(99L, 1L, 2L, "x@mail.cl", "b", 60L))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("no existe");
            verifyNoInteractions(events);
        }

        @Test
        @DisplayName("create_sesionNoWaiting_lanza409")
        void create_notWaiting() {
            LiveGameSession active = waitingSession();
            active.setStatus(SessionStatus.ACTIVE);
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(active));
            assertThatThrownBy(() -> service.create(50L, 1L, 2L, "x@mail.cl", "b", 60L))
                    .isInstanceOf(ApiException.class);
            verify(invRepo, never()).save(any());
        }

        @Test
        @DisplayName("create_ttlNull_usaDefault60")
        void create_ttlDefault() {
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(waitingSession()));
            stubSaveEchoesWithId();
            service.create(50L, 1L, 2L, "r@mail.cl", null, null);
            ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
            verify(events).publishInviteCreated(anyLong(), anyLong(), anyLong(), any(),
                    any(), any(), any(), any(), ttl.capture());
            assertThat(ttl.getValue()).isEqualTo(InvitationService.DEFAULT_TTL_SECONDS);
        }

        @Test
        @DisplayName("create_ttlSobreMax_seClampeaAlMaximo")
        void create_ttlClampMax() {
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(waitingSession()));
            stubSaveEchoesWithId();
            service.create(50L, 1L, 2L, "r@mail.cl", "b", 99_999L);
            ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
            verify(events).publishInviteCreated(anyLong(), anyLong(), anyLong(), any(),
                    any(), any(), any(), any(), ttl.capture());
            assertThat(ttl.getValue()).isEqualTo(InvitationService.MAX_TTL_SECONDS);
        }

        @Test
        @DisplayName("create_ttlBajoMin_seClampeaAlMinimo_yColorDefaultB")
        void create_ttlClampMinAndDefaultColor() {
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(waitingSession()));
            stubSaveEchoesWithId();
            ArgumentCaptor<GameInvitation> saved = ArgumentCaptor.forClass(GameInvitation.class);

            service.create(50L, 1L, 2L, "r@mail.cl", null, 1L);

            verify(invRepo).save(saved.capture());
            assertThat(saved.getValue().getInviteeColor()).isEqualTo("b");
            ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
            verify(events).publishInviteCreated(anyLong(), anyLong(), anyLong(), any(),
                    any(), any(), any(), any(), ttl.capture());
            assertThat(ttl.getValue()).isEqualTo(InvitationService.MIN_TTL_SECONDS);
        }

        @Test
        @DisplayName("create_invitadoSinCuenta_toPlayerIdNull")
        void create_emailOnly() {
            when(sessionRepo.findById(50L)).thenReturn(Optional.of(waitingSession()));
            stubSaveEchoesWithId();
            GameInvitation inv = service.create(50L, 1L, null, "sincuenta@mail.cl", "b", 60L);
            assertThat(inv.getToPlayerId()).isNull();
        }
    }

    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("accept_pendingVigente_marcaAcceptedYPublica")
        void accept_happy() {
            when(invRepo.findById(77L)).thenReturn(
                    Optional.of(pending(2L, Instant.now().plusSeconds(30))));
            when(invRepo.save(any())).thenAnswer(a -> a.getArgument(0));

            GameInvitation inv = service.accept(77L, 2L);

            assertThat(inv.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(inv.getRespondedAt()).isNotNull();
            verify(events).publishInviteAccepted(eq(77L), eq(50L), any(Instant.class));
        }

        @Test
        @DisplayName("accept_inexistente_lanza404")
        void accept_notFound() {
            when(invRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.accept(1L, 2L)).isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("accept_yaRespondida_lanza409")
        void accept_notPending() {
            GameInvitation done = pending(2L, Instant.now().plusSeconds(30));
            done.setStatus(InvitationStatus.ACCEPTED);
            when(invRepo.findById(77L)).thenReturn(Optional.of(done));
            assertThatThrownBy(() -> service.accept(77L, 2L)).isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("accept_vencida_marcaExpiredPublicaYLanza410")
        void accept_expired() {
            when(invRepo.findById(77L)).thenReturn(
                    Optional.of(pending(2L, Instant.now().minusSeconds(5))));
            when(invRepo.save(any())).thenAnswer(a -> a.getArgument(0));

            assertThatThrownBy(() -> service.accept(77L, 2L))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("expiró");
            verify(events).publishInviteExpired(eq(77L), eq(50L), any(Instant.class));
            verify(events, never()).publishInviteAccepted(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("accept_otroJugador_lanza403")
        void accept_wrongInvitee() {
            when(invRepo.findById(77L)).thenReturn(
                    Optional.of(pending(2L, Instant.now().plusSeconds(30))));
            assertThatThrownBy(() -> service.accept(77L, 999L))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("no es para este jugador");
        }
    }

    @Nested
    @DisplayName("decline / get / expireDue")
    class Rest {

        @Test
        @DisplayName("decline_conMotivo_marcaDeclinedYPublica")
        void decline_happy() {
            when(invRepo.findById(77L)).thenReturn(
                    Optional.of(pending(2L, Instant.now().plusSeconds(30))));
            when(invRepo.save(any())).thenAnswer(a -> a.getArgument(0));

            GameInvitation inv = service.decline(77L, 2L, "no puedo ahora");

            assertThat(inv.getStatus()).isEqualTo(InvitationStatus.DECLINED);
            assertThat(inv.getDeclineReason()).isEqualTo("no puedo ahora");
            verify(events).publishInviteDeclined(77L, "no puedo ahora");
        }

        @Test
        @DisplayName("get_pendingVigente_devuelveSinCambios")
        void get_happy() {
            GameInvitation p = pending(2L, Instant.now().plusSeconds(30));
            when(invRepo.findById(77L)).thenReturn(Optional.of(p));
            GameInvitation inv = service.get(77L);
            assertThat(inv.getStatus()).isEqualTo(InvitationStatus.PENDING);
            verify(events, never()).publishInviteExpired(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("get_pendingVencida_expiraPerezosamente")
        void get_lazyExpire() {
            when(invRepo.findById(77L)).thenReturn(
                    Optional.of(pending(2L, Instant.now().minusSeconds(1))));
            when(invRepo.save(any())).thenAnswer(a -> a.getArgument(0));

            GameInvitation inv = service.get(77L);

            assertThat(inv.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
            verify(events).publishInviteExpired(eq(77L), eq(50L), any(Instant.class));
        }

        @Test
        @DisplayName("get_inexistente_lanza404")
        void get_notFound() {
            when(invRepo.findById(1L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.get(1L)).isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("expireDue_conVencidas_lasMarcaYCuenta")
        void expireDue_withDue() {
            List<GameInvitation> due = List.of(
                    pending(2L, Instant.now().minusSeconds(10)),
                    pending(3L, Instant.now().minusSeconds(20)));
            when(invRepo.findByStatusAndExpiresAtBefore(eq(InvitationStatus.PENDING), any()))
                    .thenReturn(due);
            when(invRepo.save(any())).thenAnswer(a -> a.getArgument(0));

            int n = service.expireDue();

            assertThat(n).isEqualTo(2);
            verify(events, times(2)).publishInviteExpired(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("expireDue_sinVencidas_devuelveCero")
        void expireDue_empty() {
            when(invRepo.findByStatusAndExpiresAtBefore(eq(InvitationStatus.PENDING), any()))
                    .thenReturn(List.of());
            assertThat(service.expireDue()).isZero();
            verify(events, never()).publishInviteExpired(anyLong(), anyLong(), any());
        }
    }
}
