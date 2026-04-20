package cl.chessquery.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PairingResultRequest(
        @NotBlank
        @Pattern(regexp = "^(1-0|0-1|1/2-1/2)$",
                 message = "El resultado debe ser '1-0', '0-1' o '1/2-1/2'")
        String result
) {}
