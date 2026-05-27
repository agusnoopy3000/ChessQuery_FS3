package cl.chessquery.game.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link MinioStorageService}.
 *
 * <p>Mockea {@link S3Client} y {@link S3Presigner} (AWS SDK v2).</p>
 *
 * <p>Invariantes:
 * <ul>
 *   <li>El upload incluye bucket, key y content-type "application/x-chess-pgn".</li>
 *   <li>El presigned URL tiene duración de 1 hora.</li>
 * </ul></p>
 */
@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock private S3Client s3;
    @Mock private S3Presigner presigner;

    @Test
    @DisplayName("uploadPgn_validInput_putsObjectWithPgnContentType")
    void uploadPgn_validInput_putsObjectWithPgnContentType() {
        MinioStorageService svc = new MinioStorageService(s3, presigner, "chessquery-pgn");

        String returnedKey = svc.uploadPgn("games/2026/05/1.pgn", "1. e4".getBytes());

        assertThat(returnedKey).isEqualTo("games/2026/05/1.pgn");
        ArgumentCaptor<PutObjectRequest> cap = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(cap.capture(), any(RequestBody.class));
        PutObjectRequest req = cap.getValue();
        assertThat(req.bucket()).isEqualTo("chessquery-pgn");
        assertThat(req.key()).isEqualTo("games/2026/05/1.pgn");
        assertThat(req.contentType()).isEqualTo("application/x-chess-pgn");
        assertThat(req.contentLength()).isEqualTo(5L);
    }

    @Test
    @DisplayName("generatePresignedUrl_validKey_returnsPresignedUrlString")
    void generatePresignedUrl_validKey_returnsPresignedUrlString() throws Exception {
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://minio.local/chessquery-pgn/games/1.pgn?sig=abc"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        MinioStorageService svc = new MinioStorageService(s3, presigner, "chessquery-pgn");
        String url = svc.generatePresignedUrl("games/1.pgn");

        assertThat(url).isEqualTo("https://minio.local/chessquery-pgn/games/1.pgn?sig=abc");
        verify(presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }
}
