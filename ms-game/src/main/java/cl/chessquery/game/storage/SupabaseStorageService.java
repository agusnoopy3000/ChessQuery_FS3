package cl.chessquery.game.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Implementación de {@link StorageService} contra Supabase Storage.
 *
 * Endpoints utilizados:
 * - PUT  {url}/storage/v1/object/{bucket}/{key}            (upload)
 * - POST {url}/storage/v1/object/sign/{bucket}/{key}        (presigned URL)
 *
 * Requiere SERVICE_KEY (rol service_role) en el header Authorization.
 */
@Slf4j
@RequiredArgsConstructor
public class SupabaseStorageService implements StorageService {

    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String publicSupabaseUrl;
    private final String serviceKey;
    private final String bucket;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String uploadPgn(String key, byte[] pgnContent) {
        String endpoint = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucket, key);
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-chess-pgn"));
        headers.set("x-upsert", "true");

        HttpEntity<byte[]> request = new HttpEntity<>(pgnContent, headers);
        try {
            restTemplate.exchange(endpoint, HttpMethod.PUT, request, String.class);
            log.info("PGN subido a Supabase Storage: bucket={} key={} size={}",
                    bucket, key, pgnContent.length);
            return key;
        } catch (HttpStatusCodeException e) {
            log.error("Fallo upload PGN a Supabase Storage: bucket={} key={} status={}",
                    bucket, key, e.getStatusCode());
            throw new StorageException("No se pudo subir PGN a Supabase Storage", e);
        }
    }

    @Override
    public String generatePresignedUrl(String key) {
        String endpoint = String.format("%s/storage/v1/object/sign/%s/%s",
                supabaseUrl, bucket, key);
        HttpHeaders headers = baseHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of("expiresIn", 3600), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    endpoint, HttpMethod.POST, request, String.class);
            JsonNode body = mapper.readTree(response.getBody());
            String signedUrl = body.path("signedURL").asText();
            if (signedUrl.isBlank()) {
                throw new StorageException("Supabase no retornó signedURL", null);
            }
            // signedURL viene como /object/sign/{bucket}/{key}?token=...
            // Prefijamos la URL pública para que el navegador no reciba host.docker.internal.
            return publicSupabaseUrl + "/storage/v1" + signedUrl;
        } catch (HttpStatusCodeException e) {
            log.error("Fallo presign en Supabase Storage: bucket={} key={} status={}",
                    bucket, key, e.getStatusCode());
            throw new StorageException("No se pudo generar URL firmada", e);
        } catch (Exception e) {
            throw new StorageException("Error procesando respuesta de Supabase", e);
        }
    }

    private HttpHeaders baseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceKey);
        return headers;
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String msg, Throwable cause) { super(msg, cause); }
    }
}
