package cl.chessquery.gateway.filter;

import cl.chessquery.gateway.auth.PlayerIdResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la maquinaria <b>ES256 / JWKS</b> de {@link SupabaseJwtAuthFilter}
 * — el flujo asimétrico de Supabase v2 que el test HS256 ({@code
 * SupabaseJwtAuthFilterTest}) no ejercita porque la URL de Supabase no es
 * alcanzable.
 *
 * <p>Estrategia (sin dependencias nuevas):
 * <ul>
 *   <li><b>{@code refreshJwks} / {@code ecKeyFromJwk}</b>: se levanta un
 *       servidor HTTP local con {@link com.sun.net.httpserver.HttpServer} (JDK)
 *       que sirve un JWKS canónico, y se apunta el filtro a esa URL.</li>
 *   <li><b>Validación ES256 con hit en cache</b>: se inyecta la clave EC pública
 *       en {@code jwksCache} por reflexión y se firma un token ES256.</li>
 *   <li><b>{@code ecKeyFromJwk}</b> por curva: se invoca el método privado por
 *       reflexión con JWKs construidos a mano (P-256/384/521, curva no soportada,
 *       coordenadas corruptas).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupabaseJwtAuthFilterJwksTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JWT_SECRET_RAW =
            "test-secret-key-with-enough-entropy-for-hs256-validation-2026-q2";
    @SuppressWarnings("unused")
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(JWT_SECRET_RAW.getBytes(StandardCharsets.UTF_8));

    @Mock private PlayerIdResolver playerIdResolver;
    @Mock private GatewayFilterChain chain;

    private SupabaseJwtAuthFilter filter;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        // Filtro por defecto apuntando a un host que no resuelve (para los
        // tests que no usan el servidor HTTP local).
        filter = new SupabaseJwtAuthFilter(JWT_SECRET_RAW, "http://supabase.local", playerIdResolver);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    // ── 3.1 Carga del JWKS (servidor HTTP local) ────────────────────────────

    @Test
    @DisplayName("refreshJwks_pueblaCacheConClaveEC")
    void refreshJwks_pueblaCacheConClaveEC() throws Exception {
        KeyPair kp = genEc("secp256r1");
        SupabaseJwtAuthFilter f = filterServing(jwksBody("P-256", "http-kid", (ECPublicKey) kp.getPublic()), 200);

        invokeRefreshJwks(f);

        Map<String, Key> cache = cache(f);
        assertThat(cache).containsKey("http-kid");
        assertThat(cache.get("http-kid")).isInstanceOf(ECPublicKey.class);
    }

    @Test
    @DisplayName("refreshJwks_status404_noTocaCache")
    void refreshJwks_status404_noTocaCache() throws Exception {
        SupabaseJwtAuthFilter f = filterServing("{}", 404);
        invokeRefreshJwks(f);
        assertThat(cache(f)).isEmpty();
    }

    @Test
    @DisplayName("refreshJwks_jsonSinArrayKeys_noFalla")
    void refreshJwks_jsonSinArrayKeys_noFalla() throws Exception {
        SupabaseJwtAuthFilter f = filterServing("{\"keys\":\"no-soy-un-array\"}", 200);
        invokeRefreshJwks(f);
        assertThat(cache(f)).isEmpty();
    }

    @Test
    @DisplayName("refreshJwks_conexionRechazada_capturaExcepcion")
    void refreshJwks_conexionRechazada_capturaExcepcion() throws Exception {
        // Puerto 1: nada escuchando → ConnectException → cae en el catch sin propagar.
        SupabaseJwtAuthFilter f =
                new SupabaseJwtAuthFilter(JWT_SECRET_RAW, "http://127.0.0.1:1", playerIdResolver);
        invokeRefreshJwks(f);
        assertThat(cache(f)).isEmpty();
    }

    @Test
    @DisplayName("refreshJwks_jwkCurvaNoSoportada_seSalta")
    void refreshJwks_jwkCurvaNoSoportada_seSalta() throws Exception {
        // Un JWK EC con curva no soportada no debe poblar el cache (ecKeyFromJwk → null).
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode keys = root.putArray("keys");
        ObjectNode jwk = keys.addObject();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-192");
        jwk.put("kid", "weird");
        jwk.put("x", b64(BigInteger.TEN));
        jwk.put("y", b64(BigInteger.ONE));
        SupabaseJwtAuthFilter f = filterServing(MAPPER.writeValueAsString(root), 200);
        invokeRefreshJwks(f);
        assertThat(cache(f)).isEmpty();
    }

    // ── 3.2 Parseo de JWK (ecKeyFromJwk por reflexión) ──────────────────────

    @Test
    @DisplayName("ecKeyFromJwk_P256_devuelveClave")
    void ecKeyFromJwk_P256_devuelveClave() throws Exception {
        KeyPair kp = genEc("secp256r1");
        Key k = invokeEcKeyFromJwk(jwkNode("P-256", "k", (ECPublicKey) kp.getPublic()));
        assertThat(k).isInstanceOf(ECPublicKey.class);
    }

    @Test
    @DisplayName("ecKeyFromJwk_P384_devuelveClave")
    void ecKeyFromJwk_P384_devuelveClave() throws Exception {
        KeyPair kp = genEc("secp384r1");
        Key k = invokeEcKeyFromJwk(jwkNode("P-384", "k", (ECPublicKey) kp.getPublic()));
        assertThat(k).isInstanceOf(ECPublicKey.class);
    }

    @Test
    @DisplayName("ecKeyFromJwk_P521_devuelveClave")
    void ecKeyFromJwk_P521_devuelveClave() throws Exception {
        KeyPair kp = genEc("secp521r1");
        Key k = invokeEcKeyFromJwk(jwkNode("P-521", "k", (ECPublicKey) kp.getPublic()));
        assertThat(k).isInstanceOf(ECPublicKey.class);
    }

    @Test
    @DisplayName("ecKeyFromJwk_curvaNoSoportada_devuelveNull")
    void ecKeyFromJwk_curvaNoSoportada_devuelveNull() throws Exception {
        ObjectNode jwk = MAPPER.createObjectNode();
        jwk.put("crv", "P-192");
        jwk.put("kid", "k");
        jwk.put("x", b64(BigInteger.TEN));
        jwk.put("y", b64(BigInteger.ONE));
        assertThat(invokeEcKeyFromJwk(jwk)).isNull();
    }

    @Test
    @DisplayName("ecKeyFromJwk_coordenadasCorruptas_devuelveNull")
    void ecKeyFromJwk_coordenadasCorruptas_devuelveNull() throws Exception {
        ObjectNode jwk = MAPPER.createObjectNode();
        jwk.put("crv", "P-256");
        jwk.put("kid", "k");
        jwk.put("x", "***no-es-base64-url***");
        jwk.put("y", "***tampoco***");
        assertThat(invokeEcKeyFromJwk(jwk)).isNull();
    }

    // ── 3.3 Validación ES256 con hit en cache (clave inyectada) ─────────────

    @Test
    @DisplayName("filter_tokenES256ConKidEnCache_validaYPropaga")
    void filter_tokenES256ConKidEnCache_validaYPropaga() throws Exception {
        KeyPair kp = genEc("secp256r1");
        UUID uid = UUID.randomUUID();
        String token = Jwts.builder()
                .header().keyId("kid-es").and()
                .subject(uid.toString())
                .claim("email", "es@demo.cl")
                .claim("user_metadata", Map.of("role", "PLAYER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(kp.getPrivate(), Jwts.SIG.ES256)
                .compact();

        // Inyectamos la pública en el cache y marcamos el JWKS como fresco para
        // que resolveKey tome la rama "hit EC" (141-143) sin disparar refresh.
        cache(filter).put("kid-es", kp.getPublic());
        setJwksFetchedAt(filter, System.currentTimeMillis());

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games").header("Authorization", "Bearer " + token));
        when(playerIdResolver.resolve(any(), any())).thenReturn(Mono.just(3L));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    // ── 3.4 Borde de extractRole (catch) ────────────────────────────────────

    @Test
    @DisplayName("filter_roleClaimNoConvertible_defaultPlayerSinFallar")
    void filter_roleClaimNoConvertible_defaultPlayerSinFallar() {
        UUID uid = UUID.randomUUID();
        // Sin user_metadata y con "role" como objeto (no String) → claims.get(
        // "role", String.class) lanza RequiredTypeException, capturada en el
        // catch de extractRole (334-336) → retorna PLAYER y el filtro continúa.
        String token = Jwts.builder()
                .subject(uid.toString())
                .claim("email", "x@y.cl")
                .claim("role", Map.of("nested", "value"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(KEY).compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/games").header("Authorization", "Bearer " + token));
        when(playerIdResolver.resolve(any(), any())).thenReturn(Mono.just(8L));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(any());
    }

    // ── 3.5 Ciclo de vida del scheduler ─────────────────────────────────────

    @Test
    @DisplayName("startStopJwksRefresh_noLanza")
    void startStopJwksRefresh_noLanza() {
        // Métodos package-private (@PostConstruct/@PreDestroy); el test vive en
        // el mismo paquete, así que se invocan directamente.
        filter.startJwksRefresh();
        filter.stopJwksRefresh();
    }

    // ─────────────────────────── helpers ───────────────────────────────────

    /** Levanta un HttpServer local que sirve {@code body} con {@code status} en la ruta JWKS. */
    private SupabaseJwtAuthFilter filterServing(String body, int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/auth/v1/.well-known/jwks.json", ex -> {
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, payload.length == 0 ? -1 : payload.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        return new SupabaseJwtAuthFilter(JWT_SECRET_RAW, "http://127.0.0.1:" + port, playerIdResolver);
    }

    private static String jwksBody(String crv, String kid, ECPublicKey pub) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode keys = root.putArray("keys");
        keys.add(jwkNode(crv, kid, pub));
        return MAPPER.writeValueAsString(root);
    }

    private static ObjectNode jwkNode(String crv, String kid, ECPublicKey pub) {
        ECPoint w = pub.getW();
        ObjectNode n = MAPPER.createObjectNode();
        n.put("kty", "EC");
        n.put("crv", crv);
        n.put("kid", kid);
        n.put("alg", "ES256");
        n.put("x", b64(w.getAffineX()));
        n.put("y", b64(w.getAffineY()));
        return n;
    }

    /** Codifica un BigInteger como base64url sin padding, sin el byte de signo. */
    private static String b64(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            b = Arrays.copyOfRange(b, 1, b.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static KeyPair genEc(String stdName) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec(stdName));
        return g.generateKeyPair();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Key> cache(SupabaseJwtAuthFilter f) throws Exception {
        Field fld = SupabaseJwtAuthFilter.class.getDeclaredField("jwksCache");
        fld.setAccessible(true);
        return (Map<String, Key>) fld.get(f);
    }

    private static void setJwksFetchedAt(SupabaseJwtAuthFilter f, long value) throws Exception {
        Field fld = SupabaseJwtAuthFilter.class.getDeclaredField("jwksFetchedAt");
        fld.setAccessible(true);
        fld.setLong(f, value);
    }

    private static void invokeRefreshJwks(SupabaseJwtAuthFilter f) throws Exception {
        Method m = SupabaseJwtAuthFilter.class.getDeclaredMethod("refreshJwks");
        m.setAccessible(true);
        m.invoke(f);
    }

    private Key invokeEcKeyFromJwk(JsonNode jwk) throws Exception {
        Method m = SupabaseJwtAuthFilter.class.getDeclaredMethod("ecKeyFromJwk", JsonNode.class);
        m.setAccessible(true);
        return (Key) m.invoke(filter, jwk);
    }
}
