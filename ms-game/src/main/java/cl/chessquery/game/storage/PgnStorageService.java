package cl.chessquery.game.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Servicio de almacenamiento PGN en S3/MinIO.
 * Key format: games/{year}/{month}/{gameId}.pgn
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PgnStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${s3.bucket:chessquery-pgn}")
    private String bucket;

    /**
     * Sube el PGN al bucket y retorna la storage key.
     */
    public String uploadPgn(Long gameId, byte[] pgnContent) {
        LocalDate now = LocalDate.now();
        String key = String.format("games/%d/%02d/%d.pgn",
                now.getYear(), now.getMonthValue(), gameId);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-chess-pgn")
                .contentLength((long) pgnContent.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(pgnContent));
        log.info("PGN subido a S3: bucket={} key={} size={}", bucket, key, pgnContent.length);
        return key;
    }

    /**
     * Genera una URL presignada con expiración de 1 hora.
     */
    public String generatePresignedUrl(String storageKey) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(r -> r.bucket(bucket).key(storageKey))
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
