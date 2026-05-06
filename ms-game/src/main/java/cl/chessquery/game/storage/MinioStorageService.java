package cl.chessquery.game.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

/**
 * @deprecated Implementación legacy de {@link StorageService} contra MinIO/S3.
 * Mantenida para rollback (ver docs/ROLLBACK.md). Se activa con
 * {@code storage.provider=minio}.
 */
@Deprecated
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    @Override
    public String uploadPgn(String key, byte[] pgnContent) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/x-chess-pgn")
                .contentLength((long) pgnContent.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(pgnContent));
        log.info("PGN subido a MinIO: bucket={} key={} size={}", bucket, key, pgnContent.length);
        return key;
    }

    @Override
    public String generatePresignedUrl(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(1))
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
