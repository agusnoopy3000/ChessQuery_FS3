package cl.chessquery.game.dto;

import cl.chessquery.game.entity.GameType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record RegisterGameRequest(
        @NotNull Long whitePlayerId,
        @NotNull Long blackPlayerId,
        @NotBlank
        @Pattern(regexp = "^(1-0|0-1|1/2-1/2)$",
                 message = "El resultado debe ser '1-0', '0-1' o '1/2-1/2'")
        String result,
        @NotNull GameType gameType,
        Integer whiteEloBefore,
        Integer blackEloBefore,
        Integer totalMoves,
        Long tournamentPairingId,
        Integer durationSeconds,
        Instant playedAt,
        String pgnContent
) {}
