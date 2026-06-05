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
 * Cliente de la API pública de Lichess (sin OAuth) para traer los ratings
 * oficiales por modalidad de un usuario: GET /api/user/{username} → perfs.
 * Best-effort: ante cualquier error devuelve Optional.empty() y no rompe el flujo.
 */
@Slf4j
@Component
public class LichessClient {

    private static final String BASE =
            System.getenv().getOrDefault("LICHESS_API_BASE", "https://lichess.org");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record LichessRatings(Integer bullet, Integer blitz, Integer rapid, Integer classical) {}

    public Optional<LichessRatings> fetchRatings(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/api/user/" + username.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Lichess user {} → HTTP {}", username, resp.statusCode());
                return Optional.empty();
            }
            JsonNode perfs = mapper.readTree(resp.body()).path("perfs");
            return Optional.of(new LichessRatings(
                    rating(perfs, "bullet"),
                    rating(perfs, "blitz"),
                    rating(perfs, "rapid"),
                    rating(perfs, "classical")));
        } catch (Exception e) {
            log.warn("Lichess fetch falló para {}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Integer rating(JsonNode perfs, String mode) {
        JsonNode r = perfs.path(mode).path("rating");
        return r.isInt() ? r.asInt() : null;
    }
}
