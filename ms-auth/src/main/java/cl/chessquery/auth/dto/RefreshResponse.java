package cl.chessquery.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshResponse(
        String accessToken,
        @JsonProperty("expiresIn") long expiresIn
) {}