package cl.chessquery.users.util;

/**
 * Normalización de emails en UN solo punto. La columna {@code player.email}
 * es UNIQUE y se usa para matchear identidades (provision por webhook/JWT,
 * invitaciones por correo): si una escritura guarda {@code Foo@Mail.com} y
 * una búsqueda consulta {@code foo@mail.com}, el match falla y se crea un
 * perfil duplicado. Toda lectura/escritura de email debe pasar por acá.
 */
public final class Emails {

    private Emails() {
    }

    /** Trim + lowercase. Devuelve {@code null} si el email es null o queda vacío. */
    public static String normalize(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(java.util.Locale.ROOT);
        return e.isEmpty() ? null : e;
    }
}
