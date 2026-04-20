package cl.chessquery.game.dto;

public record OpeningResponse(
        Integer id,
        String ecoCode,
        String name,
        String variation
) {}
