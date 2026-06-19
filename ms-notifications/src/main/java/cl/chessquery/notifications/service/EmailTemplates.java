package cl.chessquery.notifications.service;

/**
 * Plantillas HTML de los correos transaccionales de ChessQuery (bienvenida e
 * invitación). Diseño "email-safe": tablas + estilos inline (los clientes de
 * correo ignoran CSS externo y muchos selectores), tema oscuro con acento verde
 * de la marca y fuentes con fallback a Arial/Helvetica (las web fonts no cargan
 * en la mayoría de los clientes).
 *
 * Paleta tomada del tema de la app (frontend/packages/ui-lib/src/theme):
 *   fondo #0e100d · tarjeta #181a17 · borde #2a2d27 · texto #e8ead4
 *   acento #6abf74 (verde ChessQuery) · acento claro #7ece86 · dorado #d9b25a
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    /** Par de cuerpos del correo: texto plano (fallback) + HTML. */
    public record Content(String text, String html) {}

    // ── Paleta ──────────────────────────────────────────────────────────────
    private static final String BG       = "#0e100d";
    private static final String CARD     = "#181a17";
    private static final String CARD_TOP = "#141614";
    private static final String BORDER   = "#2a2d27";
    private static final String TEXT     = "#e8ead4";
    private static final String MUTED    = "#9a9d8e";
    private static final String DIM      = "#7a7d6e";
    private static final String ACCENT   = "#6abf74";
    private static final String ACCENT_HI= "#7ece86";
    private static final String GOLD     = "#d9b25a";
    private static final String FONT     = "'Space Grotesk','IBM Plex Sans',-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif";

    /**
     * Correo de bienvenida tras el registro.
     * @param firstName nombre del jugador (o "jugador" si no hay)
     * @param portalUrl URL del portal para el CTA (si es null/blank se omite el botón)
     */
    public static Content welcome(String firstName, String portalUrl) {
        String name = (firstName != null && !firstName.isBlank()) ? esc(firstName) : "jugador";

        String text = "Hola " + name + ", tu cuenta fue creada exitosamente. "
                + "¡Bienvenido a ChessQuery! Ya podés jugar partidas en vivo, "
                + "competir en torneos y seguir tu rating."
                + (notBlank(portalUrl) ? "\n\nEntrá a ChessQuery: " + portalUrl : "");

        String cta = notBlank(portalUrl)
                ? button("Entrar a ChessQuery", portalUrl)
                : "";

        String body = """
                <h1 style="margin:0 0 14px;font-family:%1$s;font-size:24px;line-height:1.25;font-weight:700;color:%2$s;">
                  ¡Bienvenido/a, %3$s! ♞
                </h1>
                <p style="margin:0 0 22px;font-family:%1$s;font-size:15px;line-height:1.6;color:%4$s;">
                  Tu cuenta de <strong style="color:%2$s;">ChessQuery</strong> quedó lista. Desde ahora tenés todo
                  el tablero a tu disposición: jugá cuando quieras, sumáte a torneos y mirá cómo evoluciona tu juego.
                </p>
                %5$s
                %6$s
                """.formatted(FONT, TEXT, name, MUTED, featureGrid(), cta);

        return new Content(text, shell("¡Tu cuenta de ChessQuery está lista!", body));
    }

    /**
     * Correo de invitación a una partida.
     * @param inviterName quien invita (o "Un rival" si no hay)
     * @param gameUrl URL de la partida para el CTA (si es null/blank se omite el botón)
     */
    public static Content gameInvitation(String inviterName, String gameUrl) {
        String inviter = (inviterName != null && !inviterName.isBlank()) ? esc(inviterName) : "Un rival";

        String text = inviter + " te invitó a jugar una partida en ChessQuery."
                + (notBlank(gameUrl) ? "\n\nUnite a la partida: " + gameUrl : "");

        String cta = notBlank(gameUrl)
                ? button("Unirse a la partida", gameUrl)
                : "<p style=\"margin:0;font-family:" + FONT + ";font-size:13px;color:" + DIM
                  + ";\">Abrí ChessQuery para aceptar la invitación desde tus notificaciones.</p>";

        String body = """
                <div style="font-size:40px;line-height:1;margin:0 0 12px;">♕</div>
                <h1 style="margin:0 0 14px;font-family:%1$s;font-size:24px;line-height:1.25;font-weight:700;color:%2$s;">
                  Te desafiaron a una partida
                </h1>
                <p style="margin:0 0 22px;font-family:%1$s;font-size:15px;line-height:1.6;color:%4$s;">
                  <strong style="color:%5$s;">%3$s</strong> te invitó a jugar en
                  <strong style="color:%2$s;">ChessQuery</strong>. El tablero ya está puesto — solo falta tu jugada.
                </p>
                %6$s
                """.formatted(FONT, TEXT, inviter, MUTED, ACCENT_HI, cta);

        return new Content(text, shell(inviter + " te invitó a una partida en ChessQuery", body));
    }

    // ── Componentes reutilizables ─────────────────────────────────────────────

    /** Documento completo: fondo + tarjeta centrada con header de marca y footer. */
    private static String shell(String preheader, String bodyHtml) {
        return """
            <!DOCTYPE html>
            <html lang="es"><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="dark light">
            <title>ChessQuery</title>
            </head>
            <body style="margin:0;padding:0;background:%1$s;">
            <span style="display:none!important;opacity:0;color:%1$s;font-size:1px;line-height:1px;max-height:0;max-width:0;overflow:hidden;">%2$s</span>
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%1$s;padding:28px 12px;">
              <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="width:600px;max-width:600px;background:%3$s;border:1px solid %4$s;border-radius:14px;overflow:hidden;">
                  <tr><td style="background:%5$s;border-bottom:1px solid %4$s;padding:20px 32px;">
                    <span style="font-family:%6$s;font-size:20px;font-weight:700;letter-spacing:.2px;color:%7$s;">
                      <span style="color:%8$s;">♞</span> Chess<span style="color:%8$s;">Query</span>
                    </span>
                  </td></tr>
                  <tr><td style="padding:32px;">
                    %9$s
                  </td></tr>
                  <tr><td style="background:%5$s;border-top:1px solid %4$s;padding:18px 32px;">
                    <p style="margin:0;font-family:%6$s;font-size:12px;line-height:1.5;color:%10$s;">
                      Recibiste este correo desde <strong style="color:%11$s;">ChessQuery</strong>.
                      Si no esperabas este mensaje, podés ignorarlo.
                    </p>
                    <p style="margin:8px 0 0;font-family:%6$s;font-size:12px;color:%10$s;">
                      ♟ ChessQuery · plataforma de ajedrez para clubes y jugadores.
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """.formatted(BG, esc(preheader), CARD, BORDER, CARD_TOP, FONT, TEXT, ACCENT, bodyHtml, DIM, MUTED);
    }

    /** Botón CTA (bulletproof-ish: tabla + relleno, sin depender de border-radius). */
    private static String button(String label, String url) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:4px 0 8px;">
              <tr><td align="center" style="border-radius:8px;background:%1$s;">
                <a href="%2$s" target="_blank"
                   style="display:inline-block;padding:13px 30px;font-family:%3$s;font-size:15px;font-weight:700;
                          color:#111210;text-decoration:none;border-radius:8px;">
                  %4$s &rarr;
                </a>
              </td></tr>
            </table>
            """.formatted(ACCENT, esc(url), FONT, esc(label));
    }

    /** Tres "chips" de features para el correo de bienvenida. */
    private static String featureGrid() {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 24px;">
              <tr>
                %1$s%2$s%3$s
              </tr>
            </table>
            """.formatted(
                chip("♟", "Partidas en vivo", "Jugá con tablero en tiempo real"),
                chip("♔", "Torneos", "Inscribíte y competí por rondas"),
                chip("↑", "Ratings", "Seguí tu ELO de Lichess y Chess.com"));
    }

    private static String chip(String icon, String title, String desc) {
        return """
            <td width="33%%" valign="top" style="padding:0 6px;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%6$s;border:1px solid %5$s;border-radius:10px;">
                <tr><td style="padding:14px 12px;text-align:center;">
                  <div style="font-size:22px;line-height:1;color:%2$s;margin:0 0 6px;">%1$s</div>
                  <div style="font-family:%7$s;font-size:13px;font-weight:600;color:%3$s;margin:0 0 3px;">%8$s</div>
                  <div style="font-family:%7$s;font-size:11px;line-height:1.4;color:%4$s;">%9$s</div>
                </td></tr>
              </table>
            </td>
            """.formatted(icon, GOLD, TEXT, DIM, BORDER, CARD_TOP, FONT, esc(title), esc(desc));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /** Escape mínimo para interpolar texto de usuario en HTML/atributos. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
