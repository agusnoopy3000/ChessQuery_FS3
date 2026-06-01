package cl.chessquery.gateway.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link SupabaseAuthHealthIndicator}.
 *
 * <p>Construye un WebClient.Builder con un ExchangeFunction propio
 * que simula respuestas o errores sin abrir sockets.</p>
 *
 * <p>El indicador mide CONECTIVIDAD: cualquier respuesta HTTP (200 o 401) = UP;
 * solo un fallo de red/timeout = DOWN.</p>
 */
class SupabaseAuthHealthIndicatorTest {

    private SupabaseAuthHealthIndicator newIndicator(ExchangeFunction ex) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ex);
        return new SupabaseAuthHealthIndicator(builder, "http://supabase.local", "anon-test-key");
    }

    @Test
    @DisplayName("health_okResponse_returnsUpWithHttpStatus")
    void health_okResponse_returnsUp() {
        ExchangeFunction ex = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK).body("{\"name\":\"GoTrue\"}").build());
        Health h = newIndicator(ex).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsKey("endpoint").containsKey("httpStatus");
        assertThat(h.getDetails().get("httpStatus")).isEqualTo(200);
    }

    @Test
    @DisplayName("health_unauthorized_stillUp_porqueEsReachable")
    void health_unauthorized_stillUp() {
        ExchangeFunction ex = req -> Mono.just(
                ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
        Health h = newIndicator(ex).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails().get("httpStatus")).isEqualTo(401);
    }

    @Test
    @DisplayName("health_clientError_returnsDownWithErrorDetail")
    void health_clientError_returnsDownWithErrorDetail() {
        ExchangeFunction ex = req -> Mono.error(new RuntimeException("conn refused"));
        Health h = newIndicator(ex).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsKey("error");
    }
}
