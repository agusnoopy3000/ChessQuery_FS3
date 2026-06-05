package cl.chessquery.gateway.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitarios de {@link SupabaseWebhookController}.
 *
 * <p>Verifica autenticación por header X-Supabase-Webhook-Secret y publicación
 * a RabbitMQ del evento {@code user.registered}.</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Sin secret correcto → 401, no se publica nada.</li>
 *   <li>Payload vacío o sin record → 400.</li>
 *   <li>Fallo del broker → 500 (no se loggea como éxito).</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class SupabaseWebhookControllerTest {

    @Mock private RabbitTemplate rabbit;

    private static final String SECRET = "shhh";

    private SupabaseWebhookPayload validPayload() {
        SupabaseWebhookPayload.Record rec = SupabaseWebhookPayload.Record.builder()
                .id("550e8400-e29b-41d4-a716-446655440000")
                .email("a@b.cl")
                .rawUserMetaData(Map.of(
                        "role", "PLAYER", "firstName", "A", "lastName", "B",
                        "lichessUsername", "ax", "clubName", "Club"))
                .build();
        return SupabaseWebhookPayload.builder().type("INSERT").table("users")
                .schema("auth").record(rec).build();
    }

    @Test
    @DisplayName("handleUserRegistered_missingSecret_returns401")
    void handleUserRegistered_missingSecret_returns401() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        ResponseEntity<Void> resp = c.handleUserRegistered(validPayload(), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(rabbit, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    @DisplayName("handleUserRegistered_wrongSecret_returns401")
    void handleUserRegistered_wrongSecret_returns401() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        ResponseEntity<Void> resp = c.handleUserRegistered(validPayload(), "wrong");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(rabbit, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    @DisplayName("handleUserRegistered_nullPayload_returns400")
    void handleUserRegistered_nullPayload_returns400() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        ResponseEntity<Void> resp = c.handleUserRegistered(null, SECRET);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("handleUserRegistered_nullRecord_returns400")
    void handleUserRegistered_nullRecord_returns400() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        SupabaseWebhookPayload empty = SupabaseWebhookPayload.builder().build();
        ResponseEntity<Void> resp = c.handleUserRegistered(empty, SECRET);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("handleUserRegistered_validPayload_publishesAndReturns200")
    void handleUserRegistered_validPayload_publishesAndReturns200() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        ResponseEntity<Void> resp = c.handleUserRegistered(validPayload(), SECRET);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rabbit).convertAndSend(eq("ChessEvents"), eq("user.registered"), any(Object.class));
    }

    @Test
    @DisplayName("handleUserRegistered_nullMetadata_defaultsToPlayerRole")
    void handleUserRegistered_nullMetadata_defaultsToPlayerRole() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        SupabaseWebhookPayload.Record rec = SupabaseWebhookPayload.Record.builder()
                .id("uuid").email("e@x.cl").rawUserMetaData(null).build();
        SupabaseWebhookPayload p = SupabaseWebhookPayload.builder().record(rec).build();
        ResponseEntity<Void> resp = c.handleUserRegistered(p, SECRET);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("handleUserRegistered_brokerThrows_returns500")
    void handleUserRegistered_brokerThrows_returns500() {
        SupabaseWebhookController c = new SupabaseWebhookController(rabbit, SECRET);
        doThrow(new AmqpException("broker down"))
                .when(rabbit).convertAndSend(any(String.class), any(String.class), any(Object.class));
        ResponseEntity<Void> resp = c.handleUserRegistered(validPayload(), SECRET);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // --- Guard de arranque: rechazo del webhook-secret de ejemplo en producción (H-03) ---

    private static final String DEFAULT_WEBHOOK_SECRET = "dev-webhook-secret";

    @Test
    @DisplayName("constructor_defaultSecretUnderAwsProfile_abortsStartup")
    void constructor_defaultSecretUnderAwsProfile_abortsStartup() {
        org.springframework.mock.env.MockEnvironment env =
                new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("aws");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new SupabaseWebhookController(rabbit, DEFAULT_WEBHOOK_SECRET, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_WEBHOOK_SECRET");
    }

    @Test
    @DisplayName("constructor_realSecretUnderAwsProfile_startsOk")
    void constructor_realSecretUnderAwsProfile_startsOk() {
        org.springframework.mock.env.MockEnvironment env =
                new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("aws");

        org.assertj.core.api.Assertions.assertThatCode(() ->
                new SupabaseWebhookController(rabbit, "un-secreto-webhook-real-y-fuerte-2026", env))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("constructor_defaultSecretLocalProfile_startsOk")
    void constructor_defaultSecretLocalProfile_startsOk() {
        // Sin perfiles activos = ejecución local: el secreto de ejemplo se permite.
        org.springframework.mock.env.MockEnvironment env =
                new org.springframework.mock.env.MockEnvironment();

        org.assertj.core.api.Assertions.assertThatCode(() ->
                new SupabaseWebhookController(rabbit, DEFAULT_WEBHOOK_SECRET, env))
                .doesNotThrowAnyException();
    }
}
