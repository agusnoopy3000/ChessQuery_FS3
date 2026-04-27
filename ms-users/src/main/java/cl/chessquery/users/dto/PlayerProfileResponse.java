package cl.chessquery.users.dto;

import java.time.Instant;
import java.time.LocalDate;

public record PlayerProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String rut,
        LocalDate birthDate,
        String gender,
        String region,
        String fideId,
        String federationId,
        String lichessUsername,
        String enrichmentSource,
        Instant enrichedAt,
        CountryDto country,
        ClubDto    club,
        // ELO snapshots
        Integer eloNational,
        Integer eloFideStandard,
        Integer eloFideRapid,
        Integer eloFideBlitz,
        Integer eloPlatform,
        // Título vigente (null si no tiene)
        String currentTitle,
        Instant createdAt,
        Instant updatedAt
) {}
