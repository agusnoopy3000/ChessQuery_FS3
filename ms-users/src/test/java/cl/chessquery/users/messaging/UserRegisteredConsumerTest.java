package cl.chessquery.users.messaging;

import cl.chessquery.users.dto.ProvisionPlayerRequest;
import cl.chessquery.users.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock PlayerService playerService;
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
    void delegatesProvisionWithExtractedFields() {
        UUID supabaseId = UUID.randomUUID();

        consumer.onUserRegistered(message(supabaseId.toString(), "juan@demo.cl", "Juan", "Soto"));

        ArgumentCaptor<ProvisionPlayerRequest> captor =
                ArgumentCaptor.forClass(ProvisionPlayerRequest.class);
        verify(playerService).provisionBySupabaseId(captor.capture());
        ProvisionPlayerRequest req = captor.getValue();
        assertThat(req.supabaseUserId()).isEqualTo(supabaseId);
        assertThat(req.email()).isEqualTo("juan@demo.cl");
        assertThat(req.firstName()).isEqualTo("Juan");
        assertThat(req.lastName()).isEqualTo("Soto");
    }

    @Test
    void ignoresMessageWithInvalidUuid() {
        consumer.onUserRegistered(message("not-a-uuid", "x@y.cl", "X", "Y"));

        verify(playerService, never()).provisionBySupabaseId(any());
    }

    @Test
    void ignoresMessageWithoutPayload() {
        Map<String, Object> msg = new HashMap<>();
        msg.put("eventType", "user.registered");

        consumer.onUserRegistered(msg);

        verify(playerService, never()).provisionBySupabaseId(any());
    }

    @Test
    void ignoresMessageWithoutUserId() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", "x@y.cl");
        Map<String, Object> msg = new HashMap<>();
        msg.put("payload", payload);

        consumer.onUserRegistered(msg);

        verify(playerService, never()).provisionBySupabaseId(any());
    }
}
