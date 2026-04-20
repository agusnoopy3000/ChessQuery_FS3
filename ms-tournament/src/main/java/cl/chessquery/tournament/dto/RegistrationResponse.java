package cl.chessquery.tournament.dto;

import java.time.Instant;

public record RegistrationResponse(
        Long id,
        Long tournamentId,
        Long playerId,
        String status,
        Integer seedRating,
        Instant registeredAt
) {}
