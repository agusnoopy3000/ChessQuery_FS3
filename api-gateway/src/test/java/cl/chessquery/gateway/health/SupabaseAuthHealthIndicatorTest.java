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
 */
class SupabaseAuthHealthIndicatorTest {

    private SupabaseAuthHealthIndicator newIndicator(ExchangeFunction ex) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(ex);
        return new SupabaseAuthHealthIndicator(builder, "http://supabase.local");
    }

    @Test
    @DisplayName("health_okResponse_returnsUp")
    void health_okResponse_returnsUp() {
        ExchangeFunction ex = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK).body("{\"name\":\"GoTrue\"}").build());
        Health h = newIndicator(ex).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsKey("endpoint").containsKey("response");
    }

    @Test
    @DisplayName("health_truncatesLargeBody")
    void health_truncatesLargeBody() {
        String big = "x".repeat(500);
        ExchangeFunction ex = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK).body(big).build());
        Health h = newIndicator(ex).health();
        assertThat(((String) h.getDetails().get("response")).length()).isEqualTo(200);
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
