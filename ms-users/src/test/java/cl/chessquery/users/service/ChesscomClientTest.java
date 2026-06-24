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
 * Tests de {@link ChesscomClient} contra un servidor HTTP de prueba local
 * (com.sun.net.httpserver, incluido en el JDK). Espejo de {@code LichessClientTest}.
 */
class ChesscomClientTest {

    private HttpServer server;
    private ChesscomClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        int port = server.getAddress().getPort();
        client = new ChesscomClient("http://127.0.0.1:" + port);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    /** Registra una respuesta fija para GET /pub/player/{username}/stats. */
    private void stub(int status, String body) {
        server.createContext("/pub/player", exchange -> {
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
    void fetchRatings_ok_parsesAllModes() {
        stub(200, """
                {
                  "chess_bullet":{"last":{"rating":2800}},
                  "chess_blitz":{"last":{"rating":2900}},
                  "chess_rapid":{"last":{"rating":2700}},
                  "chess_daily":{"last":{"rating":1900}}
                }""");

        Optional<ChesscomClient.ChesscomRatings> r = client.fetchRatings("Hikaru");

        assertThat(r).isPresent();
        assertThat(r.get().bullet()).isEqualTo(2800);
        assertThat(r.get().blitz()).isEqualTo(2900);
        assertThat(r.get().rapid()).isEqualTo(2700);
        assertThat(r.get().daily()).isEqualTo(1900);
    }

    @Test
    void fetchRatings_missingModes_yieldNulls() {
        stub(200, "{\"chess_bullet\":{\"last\":{\"rating\":1500}}}");

        Optional<ChesscomClient.ChesscomRatings> r = client.fetchRatings("partial");

        assertThat(r).isPresent();
        assertThat(r.get().bullet()).isEqualTo(1500);
        assertThat(r.get().blitz()).isNull();
        assertThat(r.get().rapid()).isNull();
        assertThat(r.get().daily()).isNull();
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
        ChesscomClient dead = new ChesscomClient("http://127.0.0.1:1");
        assertThat(dead.fetchRatings("x")).isEmpty();
    }
}
