package cl.chessquery.users.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Cliente de la API pública de Chess.com (sin token) para traer los ratings
 * por modalidad de un usuario: GET /pub/player/{username}/stats → chess_*.last.rating.
 * Espejo de {@link LichessClient}. Best-effort: ante cualquier error devuelve
 * Optional.empty() y no rompe el flujo.
 */
@Slf4j
@Component
public class ChesscomClient {

    private final String base;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Constructor de producción: la URL base sale de CHESSCOM_API_BASE (o api.chess.com por defecto). */
    public ChesscomClient() {
        this(System.getenv().getOrDefault("CHESSCOM_API_BASE", "https://api.chess.com"));
    }

    /** Visible para tests: permite apuntar a un stub HTTP local. */
    ChesscomClient(String base) {
        this.base = base;
    }

    public record ChesscomRatings(Integer bullet, Integer blitz, Integer rapid, Integer daily) {}

    public Optional<ChesscomRatings> fetchRatings(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/pub/player/" + username.trim().toLowerCase() + "/stats"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Chess.com user {} → HTTP {}", username, resp.statusCode());
                return Optional.empty();
            }
            JsonNode stats = mapper.readTree(resp.body());
            return Optional.of(new ChesscomRatings(
                    rating(stats, "chess_bullet"),
                    rating(stats, "chess_blitz"),
                    rating(stats, "chess_rapid"),
                    rating(stats, "chess_daily")));
        } catch (Exception e) {
            log.warn("Chess.com fetch falló para {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Integer rating(JsonNode stats, String mode) {
        JsonNode r = stats.path(mode).path("last").path("rating");
        return r.isInt() ? r.asInt() : null;
    }
}
