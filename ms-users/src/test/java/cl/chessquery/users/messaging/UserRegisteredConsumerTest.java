package cl.chessquery.users.messaging;

import cl.chessquery.users.entity.Player;
import cl.chessquery.users.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock PlayerRepository playerRepo;
    @InjectMocks UserRegisteredConsumer consumer;

    private static Map<String, Object> message(String userId, String email,
                                                String firstName, String lastName) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("email", email);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        payload.put("role", "PLAYER");
        Map<String, Object> msg = new HashMap<>();
        msg.put("eventId", UUID.randomUUID().toString());
        msg.put("eventType", "user.registered");
        msg.put("payload", payload);
        return msg;
    }

    @Test
    void createsPlayerWithSupabaseUserId() {
        UUID supabaseId = UUID.randomUUID();
        when(playerRepo.findBySupabaseUserId(supabaseId)).thenReturn(Optional.empty());
        when(playerRepo.findByEmail("juan@demo.cl")).thenReturn(Optional.empty());

        consumer.onUserRegistered(message(supabaseId.toString(), "juan@demo.cl", "Juan", "Soto"));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getSupabaseUserId()).isEqualTo(supabaseId);
        assertThat(saved.getEmail()).isEqualTo("juan@demo.cl");
        assertThat(saved.getFirstName()).isEqualTo("Juan");
        assertThat(saved.getLastName()).isEqualTo("Soto");
    }

    @Test
    void linksSupabaseIdToExistingPlayerByEmail() {
        UUID supabaseId = UUID.randomUUID();
        Player existing = Player.builder().id(7L).firstName("Ana").lastName("Pérez")
                .email("ana@demo.cl").build();
        when(playerRepo.findBySupabaseUserId(supabaseId)).thenReturn(Optional.empty());
        when(playerRepo.findByEmail("ana@demo.cl")).thenReturn(Optional.of(existing));

        consumer.onUserRegistered(message(supabaseId.toString(), "ana@demo.cl", "Ana", "Pérez"));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepo).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(7L);
        assertThat(saved.getSupabaseUserId()).isEqualTo(supabaseId);
    }

    @Test
    void idempotentWhenSupabaseIdAlreadyExists() {
        UUID supabaseId = UUID.randomUUID();
        Player existing = Player.builder().id(7L).supabaseUserId(supabaseId).build();
        when(playerRepo.findBySupabaseUserId(supabaseId)).thenReturn(Optional.of(existing));

        consumer.onUserRegistered(message(supabaseId.toString(), "x@y.cl", "X", "Y"));

        verify(playerRepo, never()).save(any());
    }

    @Test
    void ignoresMessageWithInvalidUuid() {
        consumer.onUserRegistered(message("not-a-uuid", "x@y.cl", "X", "Y"));

        verify(playerRepo, never()).save(any());
    }

    @Test
    void ignoresMessageWithoutPayload() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("eventType", "user.registered");

        consumer.onUserRegistered(msg);

        verify(playerRepo, never()).save(any());
    }
}
