package cl.chessquery.game.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita las tareas programadas de ms-game (p. ej. expiración de invitaciones
 * con TTL, P1-03). Se desactiva en el perfil test para que los @Scheduled no
 * corran durante los tests de integración.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
