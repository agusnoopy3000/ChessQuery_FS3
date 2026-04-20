package cl.chessquery.tournament.dto;

import cl.chessquery.tournament.entity.TournamentFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateTournamentRequest(
        @NotBlank String name,
        String description,
        @NotNull TournamentFormat format,
        LocalDate startDate,
        LocalDate endDate,
        String location,
        @Min(2) Integer maxPlayers,
        @Min(1) Integer roundsTotal,
        Long organizerId,
        Integer minElo,
        Integer maxElo,
        String timeControl
) {}
