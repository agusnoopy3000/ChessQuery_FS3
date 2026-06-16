package cl.chessquery.users.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link LichessClient} contra un servidor HTTP de prueba local
 * (com.sun.net.httpserver, incluido en el JDK). Apuntamos el cliente al stub
 * vía el constructor visible-para-tests, sin tocar el comportamiento de prod.
 */
class LichessClientTest {

    private HttpServer server;
    private LichessClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        client = new LichessClient("http://127.0.0.1:" + port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    /** Registra una respuesta fija para GET /api/user/{username}. */
    private void stub(int status, String body) {
        server.createContext("/api/user", exchange -> {
            byte[] resp = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, resp.length == 0 ? -1 : resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
    }

    @Test
    void fetchRatings_blankOrNull_returnsEmptyWithoutCall() {
        assertThat(client.fetchRatings(null)).isEmpty();
        assertThat(client.fetchRatings("   ")).isEmpty();
    }

    @Test
    void fetchRatings_ok_parsesAllPerfs() {
        stub(200, """
                {"perfs":{
                  "bullet":{"rating":2800},
                  "blitz":{"rating":2900},
                  "rapid":{"rating":2700},
                  "classical":{"rating":2600}
                }}""");

        Optional<LichessClient.LichessRatings> r = client.fetchRatings("magnus");

        assertThat(r).isPresent();
        assertThat(r.get().bullet()).isEqualTo(2800);
        assertThat(r.get().blitz()).isEqualTo(2900);
        assertThat(r.get().rapid()).isEqualTo(2700);
        assertThat(r.get().classical()).isEqualTo(2600);
    }

    @Test
    void fetchRatings_missingModes_yieldNulls() {
        stub(200, "{\"perfs\":{\"bullet\":{\"rating\":1500}}}");

        Optional<LichessClient.LichessRatings> r = client.fetchRatings("partial");

        assertThat(r).isPresent();
        assertThat(r.get().bullet()).isEqualTo(1500);
        assertThat(r.get().blitz()).isNull();
        assertThat(r.get().rapid()).isNull();
        assertThat(r.get().classical()).isNull();
    }

    @Test
    void fetchRatings_non200_returnsEmpty() {
        stub(404, "");
        assertThat(client.fetchRatings("ghost")).isEmpty();
    }

    @Test
    void fetchRatings_malformedJson_returnsEmpty() {
        stub(200, "no soy json");
        assertThat(client.fetchRatings("broken")).isEmpty();
    }

    @Test
    void fetchRatings_connectionRefused_returnsEmpty() {
        // Cliente apuntando a un puerto cerrado → excepción de red → Optional.empty().
        LichessClient dead = new LichessClient("http://127.0.0.1:1");
        assertThat(dead.fetchRatings("x")).isEmpty();
    }
}
