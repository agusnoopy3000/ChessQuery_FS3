package cl.chessquery.tournament.dto;

public record PairingResponse(
        Long id,
        Long roundId,
        Long whitePlayerId,
        Long blackPlayerId,
        String result,
        Integer boardNumber
) {}
