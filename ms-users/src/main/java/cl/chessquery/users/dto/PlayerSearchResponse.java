package cl.chessquery.users.dto;

public record PlayerSearchResponse(
        Long    id,
        String  firstName,
        String  lastName,
        String  fideId,
        String  rut,
        String  countryIso,
        Integer eloNational,
        Integer eloFideStandard,
        String  currentTitle
) {}
