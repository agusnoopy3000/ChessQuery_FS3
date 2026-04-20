package cl.chessquery.game.dto;

import java.time.Instant;

public record PgnUrlResponse(
        String url,
        Instant expiresAt
) {}
