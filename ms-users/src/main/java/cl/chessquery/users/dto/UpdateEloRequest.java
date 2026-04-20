package cl.chessquery.users.dto;

import cl.chessquery.users.entity.RatingType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateEloRequest(
        @NotNull RatingType ratingType,
        @NotNull @Min(0) Integer newValue,
        String source
) {}
