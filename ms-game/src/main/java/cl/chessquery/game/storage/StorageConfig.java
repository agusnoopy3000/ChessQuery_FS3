package cl.chessquery.game.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Selector de proveedor de almacenamiento PGN según
 * {@code storage.provider} (default: supabase).
 *
 * - supabase → {@link SupabaseStorageService}
 * - minio    → {@link MinioStorageService}  (legacy, requiere S3Client/S3Presigner activos)
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "supabase", matchIfMissing = true)
    public StorageService supabaseStorageService(
            @org.springframework.beans.factory.annotation.Qualifier("storageRestTemplate") RestTemplate restTemplate,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.public-url:${supabase.url}}") String publicSupabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${s3.bucket:chessquery-pgn}") String bucket) {
        return new SupabaseStorageService(restTemplate, supabaseUrl, publicSupabaseUrl, serviceKey, bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
    public StorageService minioStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.bucket:chessquery-pgn}") String bucket) {
        return new MinioStorageService(s3Client, s3Presigner, bucket);
    }

    /**
     * RestTemplate "rápido" para health checks y broadcasts (chiquitos):
     * timeouts cortos (2s/3s) para que un Supabase Storage lento NO bloquee
     * el endpoint /actuator/health durante una partida en vivo.
     */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * RestTemplate dedicado para subir/descargar PGN: timeouts mas
     * generosos (5s/20s) porque los uploads de Supabase Storage pueden
     * tardar mas de lo normal en dev local. Es bean separado para no
     * afectar el health indicator ni el broadcaster.
     */
    @Bean("storageRestTemplate")
    public RestTemplate storageRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }
}
