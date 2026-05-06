package cl.chessquery.game.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Verifica que Supabase Storage responda al endpoint
 * {@code GET /storage/v1/bucket/{bucket}}. Sólo activo cuando
 * {@code storage.provider=supabase}. Tiempo objetivo: <5 s.
 */
@Component("supabaseStorage")
@ConditionalOnProperty(name = "storage.provider", havingValue = "supabase", matchIfMissing = true)
public class SupabaseStorageHealthIndicator implements HealthIndicator {

    private final RestTemplate rest;
    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;

    public SupabaseStorageHealthIndicator(RestTemplate rest,
                                           @Value("${supabase.url}") String supabaseUrl,
                                           @Value("${supabase.service-key}") String serviceKey,
                                           @Value("${s3.bucket:chessquery-pgn}") String bucket) {
        this.rest = rest;
        this.supabaseUrl = supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucket = bucket;
    }

    @Override
    public Health health() {
        String url = String.format("%s/storage/v1/bucket/%s", supabaseUrl, bucket);
        HttpHeaders headers = new HttpHeaders();
        if (serviceKey != null && !serviceKey.isBlank()) {
            headers.setBearerAuth(serviceKey);
        }
        try {
            ResponseEntity<String> r = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            return Health.up()
                    .withDetail("endpoint", url)
                    .withDetail("status", r.getStatusCode().value())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("endpoint", url)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
