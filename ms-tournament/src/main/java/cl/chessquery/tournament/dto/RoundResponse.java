package cl.chessquery.tournament.dto;

import java.time.LocalDate;
import java.util.List;

public record RoundResponse(
        Long id,
        Long tournamentId,
        int roundNumber,
        LocalDate roundDate,
        String status,
        List<PairingResponse> pairings
) {}
