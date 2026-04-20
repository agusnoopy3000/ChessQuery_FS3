package cl.chessquery.users.dto;

public record RankingEntryResponse(
        int     position,
        Long    playerId,
        String  firstName,
        String  lastName,
        String  region,
        String  clubName,
        Integer eloNational,
        Integer eloFideStandard,
        String  currentTitle,
        String  ageCategory
) {}
