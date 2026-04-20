package cl.chessquery.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        /** Segundos hasta expiración del access token. */
        @JsonProperty("expiresIn") long expiresIn
) {}