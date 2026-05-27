package cl.chessquery.gateway.filter;

import cl.chessquery.gateway.auth.PlayerIdResolver;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link SupabaseJwtAuthFilter}.
 *
 * <p>Mockea {@link PlayerIdResolver} y {@link GatewayFilterChain}. Genera
 * tokens HS256 con jjwt para ejercitar el filter sin Supabase real.</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>Rutas públicas (/auth/login, /actuator, /webhooks/*) pasan sin token.</li>
 *   <li>Header Authorization ausente o mal formado → 401.</li>
 *   <li>Token inválido o expirado → 401.</li>
 *   <li>Token válido → headers X-User-Id, X-User-Email, X-User-Role propagados.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class SupabaseJwtAuthFilterTest {

    private static final String JWT_SECRET_RAW =
            "test-secret-key-with-enough-entropy-for-hs256-validation-2026-q2";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(JWT_SECRET_RAW.getBytes(StandardCharsets.UTF_8));

    @Mock private PlayerIdResolver playerIdResolver;
    @Mock private GatewayFilterChain chain;

    private SupabaseJwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SupabaseJwtAuthFilter(JWT_SECRET_RAW, "http://supabase.local", playerIdResolver);
    }

    private String makeToken(String sub, String email, String role, Instant exp) {
        return Jwts.builder()
                .subject(sub)
                .claim("email", email)
                .claim("user_metadata", Map.of("role", role))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(exp))
                .signWith(KEY)
                .compact();
    }

    @Test
    @DisplayName("filter_publicPathLogin_skipsAuthAndCallsChain")
    void filter_publicPathLogin_skipsAuthAndCallsChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/login"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("filter_publicPathWebhook_skipsAuth")
    void filter_publicPathWebhook_skipsAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/webhooks/supabase/user-registered"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("filter_missingAuthHeader_returns401")
    void filter_missingAuthHeader_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        ServerHttpResponse resp = exchange.getResponse();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("filter_malformedBearer_returns401")
    void filter_malformedBearer_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Token abc"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("filter_expiredToken_returns401")
    void filter_expiredToken_returns401() {
        String token = makeToken(UUID.randomUUID().toString(), "a@b.cl", "PLAYER",
                Instant.now().minusSeconds(60));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Bearer " + token));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("filter_invalidSignature_returns401")
    void filter_invalidSignature_returns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Bearer not.a.real.jwt"));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("filter_subjectNotUuid_returns401")
    void filter_subjectNotUuid_returns401() {
        String token = makeToken("not-a-uuid", "a@b.cl", "PLAYER",
                Instant.now().plusSeconds(60));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Bearer " + token));
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("filter_validToken_propagatesHeadersAndCallsChain")
    void filter_validToken_propagatesHeadersAndCallsChain() {
        UUID uid = UUID.randomUUID();
        String token = makeToken(uid.toString(), "a@b.cl", "PLAYER",
                Instant.now().plusSeconds(60));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Bearer " + token));
        when(playerIdResolver.resolve(any(), any())).thenReturn(Mono.just(42L));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    @Test
    @DisplayName("filter_resolverError_returns503")
    void filter_resolverError_returns503() {
        UUID uid = UUID.randomUUID();
        String token = makeToken(uid.toString(), "a@b.cl", "PLAYER",
                Instant.now().plusSeconds(60));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games")
                        .header("Authorization", "Bearer " + token));
        when(playerIdResolver.resolve(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("ms-users down")));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("getOrder_returnsNegativeOneHundred")
    void getOrder_returnsNegativeOneHundred() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}
