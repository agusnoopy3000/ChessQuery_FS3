package cl.chessquery.game.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link LiveGameBroadcaster}.
 *
 * <p>Mockea RestTemplate; el publish es asíncrono (Executor) por lo que
 * usamos {@code verify(timeout(...))} para esperar el efecto.</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Sin configuración Supabase, no se invoca al REST template.</li>
 *   <li>Con configuración, se hace POST al endpoint de broadcast.</li>
 *   <li>Un fallo HTTP NO propaga excepción al caller.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class LiveGameBroadcasterTest {

    @Mock private RestTemplate rest;

    @Test
    @DisplayName("publish_realtimeNotConfigured_skipsHttpCall")
    void publish_realtimeNotConfigured_skipsHttpCall() throws Exception {
        LiveGameBroadcaster bc = new LiveGameBroadcaster(rest, "", "");
        bc.publish(1L, "move.played", Map.of("uci", "e2e4"));
        TimeUnit.MILLISECONDS.sleep(80);
        verify(rest, never()).exchange(any(String.class), any(HttpMethod.class), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("publish_realtimeConfigured_postsToBroadcastEndpoint")
    void publish_realtimeConfigured_postsToBroadcastEndpoint() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        LiveGameBroadcaster bc = new LiveGameBroadcaster(rest, "https://supabase.local", "service-key");
        bc.publish(42L, "move.played", Map.of("uci", "e2e4"));

        verify(rest, timeout(2000).times(1))
                .exchange(eq("https://supabase.local/realtime/v1/api/broadcast"),
                        eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("publish_restThrows_doesNotPropagate")
    void publish_restThrows_doesNotPropagate() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("supabase down"));

        LiveGameBroadcaster bc = new LiveGameBroadcaster(rest, "https://supabase.local", "service-key");

        assertThatNoException().isThrownBy(() -> bc.publish(1L, "game.finished", Map.of("k", "v")));
        verify(rest, timeout(2000).times(1))
                .exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("shutdown_callable_doesNotThrow")
    void shutdown_callable_doesNotThrow() {
        LiveGameBroadcaster bc = new LiveGameBroadcaster(rest, "", "");
        assertThatNoException().isThrownBy(bc::shutdown);
    }
}
