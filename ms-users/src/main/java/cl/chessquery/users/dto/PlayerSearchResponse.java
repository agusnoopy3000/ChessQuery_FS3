package cl.chessquery.users.dto;

public record PlayerSearchResponse(
        Long    id,
        String  firstName,
        String  lastName,
        String  fideId,
        String  federationId,
        String  rut,
        String  countryIso,
        Integer eloNational,
        Integer eloFideStandard,
        Integer eloPlatform,
        String  currentTitle,
        String  clubName,
        String  enrichmentSource
) {}
