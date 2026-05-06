package cl.chessquery.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.AlgorithmParameters;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro global que valida JWT de Supabase Auth localmente (sin llamadas HTTP externas).
 * <p>
 * Reemplaza la validación anterior que llamaba a MS-Auth GET /auth/validate.
 * La validación local elimina la latencia de red (~5-20ms por request) y el
 * punto de fallo de MS-Auth.
 * <p>
 * Extrae claims del JWT y propaga headers downstream:
 * <ul>
 *   <li>X-User-Id: sub (UUID de Supabase)</li>
 *   <li>X-User-Email: email</li>
 *   <li>X-User-Role: user_metadata.role</li>
 * </ul>
 */
@Slf4j
@Component
public class SupabaseJwtAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Rutas públicas que NO requieren autenticación JWT.
     * Incluye rutas de webhook (autenticadas por su propio mecanismo).
     */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/actuator",
            "/webhooks/"
    );

    private final SecretKey hmacKey;
    private final String jwksUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Key> jwksCache = new ConcurrentHashMap<>();
    private volatile long jwksFetchedAt = 0L;
    private static final long JWKS_TTL_MS = 5 * 60 * 1000L;

    public SupabaseJwtAuthFilter(
            @Value("${supabase.jwt-secret}") String jwtSecret,
            @Value("${supabase.url:http://host.docker.internal:54321}") String supabaseUrl) {
        this.hmacKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwksUrl = supabaseUrl.replaceAll("/+$", "") + "/auth/v1/.well-known/jwks.json";
        log.info("SupabaseJwtAuthFilter initialized; jwks={}", jwksUrl);
    }

    /**
     * Locator que selecciona la clave correcta según el header del JWT:
     * - Si tiene `kid` y la encontramos en JWKS → ES256 (Supabase asimétrico).
     * - Si no, fallback a la HMAC del JWT_SECRET (HS256).
     */
    private Key resolveKey(JwsHeader header) {
        String kid = header.getKeyId();
        String alg = header.getAlgorithm();
        if (kid != null && !kid.isEmpty() && alg != null && alg.startsWith("ES")) {
            Key k = jwksCache.get(kid);
            if (k == null || isJwksStale()) {
                refreshJwks();
                k = jwksCache.get(kid);
            }
            if (k != null) {
                return k;
            }
            log.warn("kid={} not in JWKS cache, falling back to HMAC", kid);
        }
        return hmacKey;
    }

    private boolean isJwksStale() {
        return System.currentTimeMillis() - jwksFetchedAt > JWKS_TTL_MS;
    }

    private synchronized void refreshJwks() {
        if (!isJwksStale() && !jwksCache.isEmpty()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(jwksUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("JWKS fetch returned status={}", resp.statusCode());
                return;
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode keys = root.path("keys");
            if (!keys.isArray()) return;
            for (JsonNode jwk : keys) {
                String kty = jwk.path("kty").asText();
                String kid = jwk.path("kid").asText();
                if (kid.isEmpty()) continue;
                if ("EC".equals(kty)) {
                    Key k = ecKeyFromJwk(jwk);
                    if (k != null) jwksCache.put(kid, k);
                }
            }
            jwksFetchedAt = System.currentTimeMillis();
            log.info("JWKS refreshed ({} keys)", jwksCache.size());
        } catch (Exception e) {
            log.warn("JWKS refresh failed: {}", e.getMessage());
        }
    }

    private Key ecKeyFromJwk(JsonNode jwk) {
        try {
            String crv = jwk.path("crv").asText();
            String stdName;
            if ("P-256".equals(crv)) stdName = "secp256r1";
            else if ("P-384".equals(crv)) stdName = "secp384r1";
            else if ("P-521".equals(crv)) stdName = "secp521r1";
            else { log.warn("Unsupported EC curve: {}", crv); return null; }
            byte[] xb = Base64.getUrlDecoder().decode(jwk.path("x").asText());
            byte[] yb = Base64.getUrlDecoder().decode(jwk.path("y").asText());
            ECPoint point = new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(stdName));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, ecSpec));
        } catch (Exception e) {
            log.warn("Failed to parse JWK kid={}: {}", jwk.path("kid").asText(), e.getMessage());
            return null;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Rutas públicas: no requieren JWT
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extraer token del header Authorization
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            // Validación local del JWT (firma + expiración).
            // Supabase v2 usa ES256 asimétrico (vía JWKS); fallback HS256 con JWT_SECRET.
            Claims claims = Jwts.parser()
                    .keyLocator((Locator<Key>) header -> {
                        if (header instanceof JwsHeader) {
                            return resolveKey((JwsHeader) header);
                        }
                        return hmacKey;
                    })
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Extraer claims estándar
            String userId = claims.getSubject(); // sub = UUID de Supabase
            String email = claims.get("email", String.class);

            // Extraer role de user_metadata
            String role = extractRole(claims);

            if (userId == null || userId.isEmpty()) {
                return unauthorized(exchange, "JWT missing subject claim");
            }

            log.debug("JWT validated for user={}, role={}", userId, role);

            // Propagar headers downstream (mismo formato que MS-Auth)
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set("X-User-Id", userId);
                        h.set("X-User-Email", email != null ? email : "");
                        h.set("X-User-Role", role != null ? role : "PLAYER");
                    })
                    .build();

            return chain.filter(exchange.mutate().request(mutated).build());

        } catch (ExpiredJwtException e) {
            log.debug("JWT expired for sub={}", e.getClaims() != null ? e.getClaims().getSubject() : "unknown");
            return unauthorized(exchange, "Token expired");
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Invalid token");
        }
    }

    /**
     * Extrae el role del JWT de Supabase.
     * Supabase almacena metadata de usuario en el claim "user_metadata".
     */
    @SuppressWarnings("unchecked")
    private String extractRole(Claims claims) {
        try {
            Object userMetadata = claims.get("user_metadata");
            if (userMetadata instanceof Map) {
                Object role = ((Map<String, Object>) userMetadata).get("role");
                if (role != null) {
                    return role.toString();
                }
            }

            // Fallback: intentar claim "role" directamente
            String directRole = claims.get("role", String.class);
            if (directRole != null) {
                return directRole;
            }

            // Fallback: buscar en app_metadata
            Object appMetadata = claims.get("app_metadata");
            if (appMetadata instanceof Map) {
                Object role = ((Map<String, Object>) appMetadata).get("role");
                if (role != null) {
                    return role.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting role from JWT claims: {}", e.getMessage());
        }

        return "PLAYER"; // Default role si no se encuentra
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeErrorBody(exchange, HttpStatus.UNAUTHORIZED, "Unauthorized", message);
    }

    private Mono<Void> writeErrorBody(ServerWebExchange exchange, HttpStatus status,
                                       String error, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(), error, message, java.time.Instant.now()
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Mismo orden que el filtro anterior
    }
}
