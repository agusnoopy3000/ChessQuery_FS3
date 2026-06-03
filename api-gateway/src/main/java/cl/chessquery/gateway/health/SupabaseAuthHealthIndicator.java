package cl.chessquery.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Health indicator que verifica la CONECTIVIDAD con Supabase Auth llamando
 * a {@code GET {SUPABASE_URL}/auth/v1/health}. Debe completar en <5 s.
 * No expone secrets en los detalles.
 * <p>
 * El edge de Supabase Cloud (Kong) exige el header {@code apikey} en todas las
 * rutas {@code /auth/v1/*}; sin él responde 401. Para este check lo que importa
 * es la reachability: cualquier respuesta HTTP (incluido 401) significa que
 * Supabase está accesible. Solo un fallo de red/timeout se considera DOWN.
 * Si {@code SUPABASE_ANON_KEY} está disponible se envía como {@code apikey}
 * para obtener un 200 limpio.
 */
@Component("supabaseAuth")
public class SupabaseAuthHealthIndicator implements HealthIndicator {

    private final WebClient client;
    private final String supabaseUrl;
    private final String anonKey;

    public SupabaseAuthHealthIndicator(WebClient.Builder builder,
                                        @Value("${SUPABASE_URL:http://localhost:54321}") String supabaseUrl,
                                        @Value("${SUPABASE_ANON_KEY:}") String anonKey) {
        this.client = builder.build();
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
    }

    @Override
    public Health health() {
        String endpoint = supabaseUrl + "/auth/v1/health";
        try {
            Integer status = client.get()
                    .uri(endpoint)
                    .headers(h -> {
                        if (anonKey != null && !anonKey.isBlank()) {
                            h.set("apikey", anonKey);
                            h.setBearerAuth(anonKey);
                        }
                    })
                    // exchangeToMono no lanza en 4xx/5xx: cualquier respuesta = reachable
                    .exchangeToMono(resp -> resp.releaseBody()
                            .thenReturn(resp.statusCode().value()))
                    .timeout(Duration.ofSeconds(4))
                    .block();
            return Health.up()
                    .withDetail("endpoint", endpoint)
                    .withDetail("httpStatus", status)
                    .build();
        } catch (Exception e) {
            // Solo errores de red/timeout llegan acá → Supabase inaccesible
            return Health.down()
                    .withDetail("endpoint", endpoint)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
