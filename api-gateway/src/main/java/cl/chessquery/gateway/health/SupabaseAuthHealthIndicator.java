package cl.chessquery.gateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Health indicator que verifica la conectividad con Supabase Auth llamando
 * a {@code GET {SUPABASE_URL}/auth/v1/health}. Debe completar en <5 s.
 * No expone secrets en los detalles.
 */
@Component("supabaseAuth")
public class SupabaseAuthHealthIndicator implements HealthIndicator {

    private final WebClient client;
    private final String supabaseUrl;

    public SupabaseAuthHealthIndicator(WebClient.Builder builder,
                                        @Value("${SUPABASE_URL:http://localhost:54321}") String supabaseUrl) {
        this.client = builder.build();
        this.supabaseUrl = supabaseUrl;
    }

    @Override
    public Health health() {
        try {
            String body = client.get()
                    .uri(supabaseUrl + "/auth/v1/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(4))
                    .block();
            return Health.up()
                    .withDetail("endpoint", supabaseUrl + "/auth/v1/health")
                    .withDetail("response", body != null && body.length() > 200
                            ? body.substring(0, 200) : body)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", supabaseUrl + "/auth/v1/health")
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
