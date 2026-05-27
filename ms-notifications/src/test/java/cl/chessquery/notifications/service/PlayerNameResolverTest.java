package cl.chessquery.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link PlayerNameResolver}.
 *
 * <p>Reemplaza el RestTemplate interno por uno mockeable vía reflection.
 * Verifica cache, fallback ante fallo y manejo de null/blank.</p>
 */
@ExtendWith(MockitoExtension.class)
class PlayerNameResolverTest {

    private PlayerNameResolver newResolver(RestTemplate stub) {
        PlayerNameResolver r = new PlayerNameResolver(new RestTemplateBuilder(), "http://stub");
        ReflectionTestUtils.setField(r, "http", stub);
        return r;
    }

    @Test
    @DisplayName("resolve_nullId_returnsGenericPlaceholder")
    void resolve_nullId_returnsGenericPlaceholder() {
        PlayerNameResolver r = newResolver(mock(RestTemplate.class));
        assertThat(r.resolve(null)).isEqualTo("un jugador");
    }

    @Test
    @DisplayName("resolve_remoteSuccess_returnsFullName")
    void resolve_remoteSuccess_returnsFullName() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(eq("http://stub/users/5/profile"), eq(Map.class)))
                .thenReturn(Map.of("firstName", "Ana", "lastName", "Soto"));
        PlayerNameResolver r = newResolver(rest);
        assertThat(r.resolve(5L)).isEqualTo("Ana Soto");
    }

    @Test
    @DisplayName("resolve_cachedSecondCall_skipsHttp")
    void resolve_cachedSecondCall_skipsHttp() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(any(String.class), eq(Map.class)))
                .thenReturn(Map.of("firstName", "Ana", "lastName", "Soto"));
        PlayerNameResolver r = newResolver(rest);
        r.resolve(5L);
        r.resolve(5L);
        verify(rest, times(1)).getForObject(any(String.class), eq(Map.class));
    }

    @Test
    @DisplayName("resolve_remoteFails_returnsFallback")
    void resolve_remoteFails_returnsFallback() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(any(String.class), eq(Map.class)))
                .thenThrow(new RestClientException("down"));
        PlayerNameResolver r = newResolver(rest);
        assertThat(r.resolve(7L)).isEqualTo("jugador #7");
    }

    @Test
    @DisplayName("resolve_nullBody_returnsFallback")
    void resolve_nullBody_returnsFallback() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(any(String.class), eq(Map.class))).thenReturn(null);
        PlayerNameResolver r = newResolver(rest);
        assertThat(r.resolve(7L)).isEqualTo("jugador #7");
    }

    @Test
    @DisplayName("resolve_emptyName_returnsFallback")
    void resolve_emptyName_returnsFallback() {
        RestTemplate rest = mock(RestTemplate.class);
        when(rest.getForObject(any(String.class), eq(Map.class)))
                .thenReturn(Map.of("firstName", "", "lastName", ""));
        PlayerNameResolver r = newResolver(rest);
        assertThat(r.resolve(7L)).isEqualTo("jugador #7");
    }
}
