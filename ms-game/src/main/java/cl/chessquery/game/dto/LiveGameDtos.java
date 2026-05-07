package cl.chessquery.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class LiveGameDtos {
    private LiveGameDtos() {}

    /** POST /games/live  (creador = white). */
    public record CreateLiveGameRequest(
            @NotNull Long whitePlayerId,
            Integer whiteEloBefore,
            Long timeControlInitialMs,
            Long timeControlIncrementMs
    ) {}

    /** POST /games/live/{id}/join. */
    public record JoinLiveGameRequest(
            @NotNull Long playerId,
            Integer eloBefore
    ) {}

    /** POST /games/live/{id}/move. */
    public record MoveRequest(
            @NotNull Long playerId,
            @NotBlank
            @Pattern(regexp = "^[a-h][1-8][a-h][1-8][qrbn]?$",
                     message = "uci debe ser e2e4, e7e8q, etc.")
            String uci,
            Long clockWhiteMs,
            Long clockBlackMs
    ) {}

    /** POST /games/live/{id}/resign. */
    public record ResignRequest(@NotNull Long playerId) {}

    public record LiveMoveResponse(
            Integer moveNumber,
            String color,
            String uci,
            String san,
            String fenAfter,
            Long clockWhiteMs,
            Long clockBlackMs,
            Instant createdAt
    ) {}

    public record LiveGameResponse(
            Long id,
            Long whitePlayerId,
            Long blackPlayerId,
            String status,
            String currentFen,
            String turn,
            String result,
            String endReason,
            Long timeControlInitialMs,
            Long timeControlIncrementMs,
            Long clockWhiteMs,
            Long clockBlackMs,
            Long finalizedGameId,
            List<LiveMoveResponse> moves,
            Instant startedAt,
            Instant finishedAt,
            Instant lastMoveAt,
            String detectedOpeningEco,
            String detectedOpeningName
    ) {}
}
