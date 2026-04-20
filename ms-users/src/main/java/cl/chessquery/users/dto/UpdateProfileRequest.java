package cl.chessquery.users.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String lastName,
        Integer clubId,
        @Size(max = 100) String region
) {}
