package cl.chessquery.game.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link SupabaseStorageHealthIndicator}.
 *
 * <p>Mockea {@link RestTemplate} y verifica que el HealthIndicator
 * reporta UP en respuesta 2xx y DOWN ante cualquier excepción.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupabaseStorageHealthIndicatorTest {

    @Mock private RestTemplate rest;

    private static final String URL = "https://supabase.local";
    private static final String KEY = "service-key";
    private static final String BUCKET = "chessquery-pgn";

    @Test
    @DisplayName("health_2xxResponse_reportsUpWithStatusDetail")
    void health_2xxResponse_reportsUpWithStatusDetail() {
        when(rest.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        SupabaseStorageHealthIndicator h =
                new SupabaseStorageHealthIndicator(rest, URL, KEY, BUCKET);
        Health result = h.health();

        assertThat(result.getStatus()).isEqualTo(Status.UP);
        assertThat(result.getDetails()).containsEntry("status", 200)
                .containsKey("endpoint");
    }

    @Test
    @DisplayName("health_restThrows_reportsDownWithErrorDetail")
    void health_restThrows_reportsDownWithErrorDetail() {
        when(rest.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("supabase down"));

        SupabaseStorageHealthIndicator h =
                new SupabaseStorageHealthIndicator(rest, URL, KEY, BUCKET);
        Health result = h.health();

        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails()).containsEntry("error", "RestClientException")
                .containsKey("endpoint");
    }

    @Test
    @DisplayName("health_blankServiceKey_doesNotSetBearerAuthButStillExecutes")
    void health_blankServiceKey_doesNotSetBearerAuthButStillExecutes() {
        when(rest.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));

        SupabaseStorageHealthIndicator h =
                new SupabaseStorageHealthIndicator(rest, URL, "", BUCKET);
        Health result = h.health();

        assertThat(result.getStatus()).isEqualTo(Status.UP);
    }
}
