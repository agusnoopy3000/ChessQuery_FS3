package cl.chessquery.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Provisión idempotente de un Player a partir de un usuario de Supabase Auth.
 * Se usa desde dos puntos:
 * <ul>
 *   <li>{@code POST /users/provision} llamado por el API Gateway cuando un
 *       JWT válido llega y el Player aún no existe (registro reciente o
 *       webhook caído).</li>
 *   <li>{@code UserRegisteredConsumer} cuando llega el evento
 *       {@code user.registered} desde RabbitMQ.</li>
 * </ul>
 *
 * Comportamiento:
 * <ol>
 *   <li>Si ya existe Player con ese {@code supabaseUserId} → no-op (devuelve
 *       el existente).</li>
 *   <li>Si hay match por email → asocia {@code supabaseUserId} al existente.</li>
 *   <li>Si no, crea Player nuevo con los datos provistos.</li>
 * </ol>
 */
public record ProvisionPlayerRequest(
        @NotNull UUID supabaseUserId,
        String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 100) String lichessUsername,
        @Size(max = 200) String clubName
) {}
