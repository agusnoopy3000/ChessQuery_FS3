package cl.chessquery.game.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Broadcastea eventos de partida viva a Supabase Realtime usando el endpoint
 * REST de "broadcast" autenticado con SERVICE_KEY. El frontend escucha el
 * canal `game:{id}` con el anon key (sin RLS porque broadcast pasa por
 * Realtime, no por Postgres).
 *
 * Eventos: game.started, move.played, game.finished, draw.offered.
 *
 * Si Supabase Realtime no está disponible, sólo loguea — el cliente puede
 * recuperar el estado vía GET /games/live/{id} en cada reconexión, así que
 * Realtime es optimización, no fuente de verdad.
 */
@Slf4j
@Component
public class LiveGameBroadcaster {

    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String supabaseUrl;
    private final String serviceKey;

    /**
     * Pool de hilos para broadcasts fire-and-forget. El response del move
     * NO debe esperar al POST a Supabase Realtime (50-200ms en local) — eso
     * agrega latencia entre que A juega y B ve el movimiento.
     */
    private final ExecutorService broadcastPool = Executors.newFixedThreadPool(
            4, r -> { Thread t = new Thread(r, "live-broadcast"); t.setDaemon(true); return t; });

    public LiveGameBroadcaster(RestTemplate rest,
                                @Value("${supabase.url:}") String supabaseUrl,
                                @Value("${supabase.service-key:}") String serviceKey) {
        this.rest = rest;
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
    }

    public void publish(Long gameId, String eventType, Map<String, Object> payload) {
        if (supabaseUrl == null || supabaseUrl.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            log.debug("Realtime no configurado; broadcast {} game:{} omitido", eventType, gameId);
            return;
        }
        broadcastPool.submit(() -> doPublish(gameId, eventType, payload));
    }

    private void doPublish(Long gameId, String eventType, Map<String, Object> payload) {
        String url = supabaseUrl + "/realtime/v1/api/broadcast";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceKey);
        headers.set("apikey", serviceKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> message = Map.of(
                "topic", "game:" + gameId,
                "event", eventType,
                "payload", payload,
                "private", false
        );
        Map<String, Object> body = Map.of("messages", new Object[]{message});
        try {
            rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            log.debug("Broadcast {} → game:{}", eventType, gameId);
        } catch (Exception e) {
            // No interrumpir el flujo del juego por un fallo en realtime.
            log.warn("Broadcast {} → game:{} falló: {}", eventType, gameId, e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        broadcastPool.shutdown();
        try { broadcastPool.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }
}
