package cl.chessquery.notifications.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de las plantillas HTML de correo: estructura, branding, escape de
 * entradas y manejo de URL/nombre ausentes (sin botón).
 */
class EmailTemplatesTest {

    @Test
    @DisplayName("welcome incluye nombre, marca y CTA cuando hay portalUrl")
    void welcome_withName_andUrl() {
        EmailTemplates.Content c = EmailTemplates.welcome("Cristóbal", "https://portal.test");
        assertThat(c.html()).contains("Cristóbal").contains("ChessQuery").contains("https://portal.test");
        assertThat(c.html()).startsWith("<!DOCTYPE html>");
        assertThat(c.text()).contains("Cristóbal").contains("https://portal.test");
    }

    @Test
    @DisplayName("welcome sin nombre usa 'jugador' y sin URL omite el botón")
    void welcome_noName_noUrl_omitsButton() {
        EmailTemplates.Content c = EmailTemplates.welcome("  ", null);
        assertThat(c.html()).contains("jugador");
        assertThat(c.html()).doesNotContain("href=");   // sin CTA
        assertThat(c.text()).doesNotContain("Entrá a ChessQuery:");
    }

    @Test
    @DisplayName("gameInvitation incluye invitador y enlace a la partida")
    void invitation_withInviter_andUrl() {
        EmailTemplates.Content c = EmailTemplates.gameInvitation("Ana", "https://portal/play/9");
        assertThat(c.html()).contains("Ana").contains("https://portal/play/9").contains("Unirse a la partida");
        assertThat(c.text()).contains("Ana").contains("https://portal/play/9");
    }

    @Test
    @DisplayName("gameInvitation sin URL no rompe y omite el botón")
    void invitation_noUrl() {
        EmailTemplates.Content c = EmailTemplates.gameInvitation("Ana", null);
        assertThat(c.html()).doesNotContain("href=");
        assertThat(c.html()).contains("Ana");
    }

    @Test
    @DisplayName("escapa caracteres HTML en el nombre (anti-inyección)")
    void escapesHtmlInName() {
        EmailTemplates.Content c = EmailTemplates.welcome("<script>x</script>", "https://p.test");
        assertThat(c.html()).doesNotContain("<script>x</script>");
        assertThat(c.html()).contains("&lt;script&gt;");
    }
}
