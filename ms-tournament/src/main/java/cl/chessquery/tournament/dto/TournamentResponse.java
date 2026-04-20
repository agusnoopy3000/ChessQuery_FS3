package cl.chessquery.tournament.dto;

import java.time.Instant;
import java.time.LocalDate;

public record TournamentResponse(
        Long id,
        String name,
        String description,
        String format,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String location,
        Integer maxPlayers,
        Integer roundsTotal,
        Long organizerId,
        Integer minElo,
        Integer maxElo,
        String timeControl,
        int registeredCount,
        Instant createdAt
) {}
