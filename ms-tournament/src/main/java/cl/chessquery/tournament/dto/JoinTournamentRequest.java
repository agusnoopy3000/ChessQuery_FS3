package cl.chessquery.tournament.dto;

import jakarta.validation.constraints.NotNull;

public record JoinTournamentRequest(
        @NotNull Long playerId
) {}
