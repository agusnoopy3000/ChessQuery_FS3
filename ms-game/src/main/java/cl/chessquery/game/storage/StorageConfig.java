package cl.chessquery.game.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
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
            RestTemplate restTemplate,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${s3.bucket:chessquery-pgn}") String bucket) {
        return new SupabaseStorageService(restTemplate, supabaseUrl, serviceKey, bucket);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "minio")
    public StorageService minioStorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${s3.bucket:chessquery-pgn}") String bucket) {
        return new MinioStorageService(s3Client, s3Presigner, bucket);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
