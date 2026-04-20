package cl.chessquery.tournament.dto;

import cl.chessquery.tournament.entity.TournamentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusTransitionRequest(
        @NotNull TournamentStatus newStatus
) {}
