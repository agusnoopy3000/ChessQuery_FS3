package cl.chessquery.game.storage;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

class SupabaseStorageServiceTest {

    private final RestTemplate rest = mock(RestTemplate.class);
    private final SupabaseStorageService svc =
            new SupabaseStorageService(rest, "http://localhost:54321", "svc-key", "chessquery-pgn");

    @Test
    void uploadPgn_callsCorrectEndpointWithBearer() {
        String key = "games/2026/05/42.pgn";
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{}", OK));

        String returned = svc.uploadPgn(key, "[Event \"X\"]\n1. e4".getBytes(StandardCharsets.UTF_8));

        assertThat(returned).isEqualTo(key);
        verify(rest, times(1)).exchange(
                eq("http://localhost:54321/storage/v1/object/chessquery-pgn/games/2026/05/42.pgn"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void uploadPgn_wrapsHttpErrorInStorageException() {
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(BAD_REQUEST, "rejected"));

        assertThatThrownBy(() -> svc.uploadPgn("games/x.pgn", new byte[0]))
                .isInstanceOf(SupabaseStorageService.StorageException.class)
                .hasMessageContaining("subir PGN");
    }

    @Test
    void generatePresignedUrl_returnsFullUrl() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(
                        "{\"signedURL\":\"/object/sign/chessquery-pgn/games/x.pgn?token=abc\"}", OK));

        String url = svc.generatePresignedUrl("games/x.pgn");

        assertThat(url).isEqualTo(
                "http://localhost:54321/storage/v1/object/sign/chessquery-pgn/games/x.pgn?token=abc");
    }

    @Test
    void generatePresignedUrl_wrapsHttpErrorInStorageException() {
        when(rest.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(BAD_REQUEST, "no token"));

        assertThatThrownBy(() -> svc.generatePresignedUrl("games/x.pgn"))
                .isInstanceOf(SupabaseStorageService.StorageException.class);
    }

    @Test
    void uploadPgn_includesAuthorizationHeader() {
        when(rest.exchange(any(String.class), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    HttpEntity<?> req = inv.getArgument(2);
                    HttpHeaders headers = req.getHeaders();
                    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer svc-key");
                    assertThat(headers.getContentType().toString()).isEqualTo("application/x-chess-pgn");
                    return new ResponseEntity<>("{}", OK);
                });

        svc.uploadPgn("k", new byte[]{1, 2, 3});
    }
}
