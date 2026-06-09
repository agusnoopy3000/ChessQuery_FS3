package cl.chessquery.gateway.filter;

import cl.chessquery.gateway.auth.PlayerIdResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests del guard de arranque que rechaza el secreto JWT de ejemplo en
 * producción (mitigación de H-03 del informe de seguridad).
 *
 * <p>Regla: con el perfil {@code aws} activo, el secreto de ejemplo público
 * de Supabase debe abortar el arranque. En local (sin perfil) se permite.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupabaseJwtAuthFilterSecretGuardTest {

    private static final String DEFAULT_SECRET =
            "super-secret-jwt-token-with-at-least-32-characters-long";
    private static final String REAL_SECRET =
            "un-secreto-real-de-supabase-con-suficiente-entropia-2026";

    @Mock(strictness = Mock.Strictness.LENIENT)
    private PlayerIdResolver playerIdResolver;

    @Test
    @DisplayName("constructor_defaultSecretUnderAwsProfile_abortsStartup")
    void constructor_defaultSecretUnderAwsProfile_abortsStartup() {
        MockEnvironment env = new MockEnvironment().withProperty("x", "y");
        env.setActiveProfiles("aws");

        assertThatThrownBy(() -> new SupabaseJwtAuthFilter(
                DEFAULT_SECRET, "http://supabase.local", env, playerIdResolver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPABASE_JWT_SECRET");
    }

    @Test
    @DisplayName("constructor_realSecretUnderAwsProfile_startsOk")
    void constructor_realSecretUnderAwsProfile_startsOk() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("aws");

        assertThatCode(() -> new SupabaseJwtAuthFilter(
                REAL_SECRET, "http://supabase.local", env, playerIdResolver))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("constructor_defaultSecretLocalProfile_startsOk")
    void constructor_defaultSecretLocalProfile_startsOk() {
        // Sin perfiles activos = ejecución local: el secreto de ejemplo se permite.
        MockEnvironment env = new MockEnvironment();

        assertThatCode(() -> new SupabaseJwtAuthFilter(
                DEFAULT_SECRET, "http://supabase.local", env, playerIdResolver))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("constructor_defaultSecretOtherProfile_startsOk")
    void constructor_defaultSecretOtherProfile_startsOk() {
        // Un perfil que no sea 'aws' (ej. 'dev') no debe disparar el guard.
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        assertThatCode(() -> new SupabaseJwtAuthFilter(
                DEFAULT_SECRET, "http://supabase.local", env, playerIdResolver))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("constructor_convenienceCtorWithDefaultSecret_startsOk")
    void constructor_convenienceCtorWithDefaultSecret_startsOk() {
        // El constructor de 3 args (usado por tests) usa StandardEnvironment
        // sin perfiles → nunca bloquea, aun con el secreto de ejemplo.
        assertThatCode(() -> new SupabaseJwtAuthFilter(
                DEFAULT_SECRET, "http://supabase.local", playerIdResolver))
                .doesNotThrowAnyException();
    }
}
