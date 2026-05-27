package cl.chessquery.tournament.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link UserEloClient}.
 *
 * <p>Mockea RestTemplate y CircuitBreakerFactory. El stub del CircuitBreaker
 * ejecuta el supplier (caso normal) o el fallback (cuando ms-users falla).</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Si ms-users responde, devuelve el ELO informado.</li>
 *   <li>Si el body es null o no tiene eloNational, devuelve 1500.</li>
 *   <li>Si el circuit breaker se abre, el fallback devuelve 1500.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class UserEloClientTest {

    @Mock private RestTemplate rest;
    @Mock private CircuitBreakerFactory<?, ?> cbFactory;

    private UserEloClient client(CircuitBreaker cb) {
        when(cbFactory.create(anyString())).thenAnswer(inv -> cb);
        UserEloClient c = new UserEloClient(rest, cbFactory);
        ReflectionTestUtils.setField(c, "msUsersUrl", "http://ms-users:8081");
        return c;
    }

    private CircuitBreaker passthroughCircuitBreaker() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        when(cb.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        return cb;
    }

    private CircuitBreaker fallbackCircuitBreaker() {
        CircuitBreaker cb = mock(CircuitBreaker.class);
        when(cb.run(any(Supplier.class), any(Function.class)))
                .thenAnswer(inv -> ((Function<Throwable, ?>) inv.getArgument(1))
                        .apply(new RuntimeException("breaker open")));
        return cb;
    }

    @Test
    @DisplayName("getElo_msUsersReturnsElo_returnsThatValue")
    void getElo_msUsersReturnsElo_returnsThatValue() {
        UserEloClient c = client(passthroughCircuitBreaker());
        when(rest.getForObject(eq("http://ms-users:8081/users/1/profile"), eq(Map.class)))
                .thenReturn(Map.of("eloNational", 1800));
        assertThat(c.getElo(1L)).isEqualTo(1800);
    }

    @Test
    @DisplayName("getElo_msUsersReturnsNullBody_defaultsTo1500")
    void getElo_msUsersReturnsNullBody_defaultsTo1500() {
        UserEloClient c = client(passthroughCircuitBreaker());
        when(rest.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        assertThat(c.getElo(1L)).isEqualTo(1500);
    }

    @Test
    @DisplayName("getElo_msUsersBodyMissingEloField_defaultsTo1500")
    void getElo_msUsersBodyMissingEloField_defaultsTo1500() {
        UserEloClient c = client(passthroughCircuitBreaker());
        when(rest.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("name", "x"));
        assertThat(c.getElo(1L)).isEqualTo(1500);
    }

    @Test
    @DisplayName("getElo_circuitBreakerOpen_returnsFallback1500")
    void getElo_circuitBreakerOpen_returnsFallback1500() {
        UserEloClient c = client(fallbackCircuitBreaker());
        assertThat(c.getElo(99L)).isEqualTo(1500);
    }
}
