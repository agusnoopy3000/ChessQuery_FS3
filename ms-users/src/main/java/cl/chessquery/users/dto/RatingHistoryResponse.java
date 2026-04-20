package cl.chessquery.users.dto;

import java.time.Instant;

public record RatingHistoryResponse(
        Long    id,
        String  ratingType,
        Integer ratingValue,
        Integer ratingPrevValue,
        Short   delta,
        Instant recordedAt,
        String  source
) {}
