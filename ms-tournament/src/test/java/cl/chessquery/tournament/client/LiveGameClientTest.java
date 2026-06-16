package cl.chessquery.tournament.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link LiveGameClient}: ejecuta el supplier del circuit breaker para
 * cubrir el camino feliz y los casos en que ms-game responde mal, y ejecuta el
 * fallback para el caso de breaker abierto / ms-game caído.
 */
@ExtendWith(MockitoExtension.class)
class LiveGameClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock @SuppressWarnings("rawtypes") private CircuitBreakerFactory circuitBreakerFactory;
    @Mock private CircuitBreaker circuitBreaker;

    @InjectMocks private LiveGameClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReflectionTestUtils.setField(client, "msGameUrl", "http://ms-game:8083");
        when(circuitBreakerFactory.create("ms-game")).thenReturn(circuitBreaker);
    }

    /** Hace que cb.run(...) ejecute el supplier (camino normal). */
    @SuppressWarnings("unchecked")
    private void runExecutesSupplier() {
        when(circuitBreaker.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
    }

    @Test
    void createTournamentGame_ok_returnsSessionId() {
        runExecutesSupplier();
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("id", 555));

        Long id = client.createTournamentGame(1L, 2L, 1500, 1600, 99L, 600000L, 5000L);

        assertThat(id).isEqualTo(555L);
    }

    @Test
    void createTournamentGame_nullBody_returnsNull() {
        runExecutesSupplier();
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class))).thenReturn(null);

        assertThat(client.createTournamentGame(1L, 2L, null, null, 99L, 600000L, 5000L)).isNull();
    }

    @Test
    void createTournamentGame_bodyWithoutId_returnsNull() {
        runExecutesSupplier();
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(Map.of("foo", "bar"));

        assertThat(client.createTournamentGame(1L, 2L, 1500, 1600, 99L, 600000L, 5000L)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createTournamentGame_msGameDown_fallbackReturnsNull() {
        // El breaker invoca el fallback (ms-game caído / breaker abierto).
        when(circuitBreaker.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Function<Throwable, Object>) inv.getArgument(1))
                        .apply(new RestClientException("ms-game down")));

        assertThat(client.createTournamentGame(1L, 2L, 1500, 1600, 99L, 600000L, 5000L)).isNull();
    }
}
