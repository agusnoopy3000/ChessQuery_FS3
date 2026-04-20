package cl.chessquery.game.dto;

import java.time.Instant;

public record GameResponse(
        Long id,
        Long whitePlayerId,
        Long blackPlayerId,
        String result,
        String gameType,
        Integer whiteEloBefore,
        Integer blackEloBefore,
        Integer whiteEloAfter,
        Integer blackEloAfter,
        Integer totalMoves,
        String openingEco,
        String openingName,
        String pgnUrl,
        Long tournamentPairingId,
        Integer durationSeconds,
        Instant playedAt,
        Instant createdAt
) {}
