package cl.chessquery.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Sincroniza un AuthUser recién registrado con su Player en ms-users.
 * El portal llama POST /users/sync tras un /auth/register exitoso para
 * crear el Player con id = AuthUser.id e incluyendo el lichessUsername
 * opcional que el usuario completó en el formulario.
 */
public record AuthSyncRequest(
        @NotNull Long id,
        String email,
        @Size(min = 1, max = 100) String firstName,
        @Size(min = 1, max = 100) String lastName,
        @Size(max = 100) String lichessUsername
) {}
