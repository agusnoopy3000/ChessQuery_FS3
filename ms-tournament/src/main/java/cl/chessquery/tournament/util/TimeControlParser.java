package cl.chessquery.tournament.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el control de tiempo de un torneo (texto libre, p.ej. "90+30",
 * "Rápido 15+10", "5") al formato en milisegundos que usa ms-game.
 *
 * Convención de ajedrez: "base+incremento" con la base en MINUTOS y el
 * incremento en SEGUNDOS. Tolerante a prefijos/sufijos de texto; si no se puede
 * parsear, cae a un default razonable (5+0).
 */
public final class TimeControlParser {

    private TimeControlParser() {}

    /** Default cuando el torneo no define control de tiempo o no se puede parsear. */
    public static final long DEFAULT_INITIAL_MS = 5 * 60_000L; // 5 min
    public static final long DEFAULT_INCREMENT_MS = 0L;

    private static final Pattern PATTERN = Pattern.compile("(\\d{1,3})\\s*\\+\\s*(\\d{1,3})");
    private static final Pattern ONLY_MINUTES = Pattern.compile("(?<![\\d+])(\\d{1,3})(?![\\d+])");

    public record TimeControl(long initialMs, long incrementMs) {}

    /**
     * @param raw texto del control de tiempo del torneo (puede ser null/blank).
     * @return base e incremento en ms; nunca null.
     */
    public static TimeControl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new TimeControl(DEFAULT_INITIAL_MS, DEFAULT_INCREMENT_MS);
        }
        Matcher m = PATTERN.matcher(raw);
        if (m.find()) {
            long minutes = Long.parseLong(m.group(1));
            long incrementSec = Long.parseLong(m.group(2));
            return new TimeControl(minutes * 60_000L, incrementSec * 1_000L);
        }
        // Sin "+": intentamos interpretar un único número como minutos.
        Matcher only = ONLY_MINUTES.matcher(raw);
        if (only.find()) {
            long minutes = Long.parseLong(only.group(1));
            return new TimeControl(minutes * 60_000L, 0L);
        }
        return new TimeControl(DEFAULT_INITIAL_MS, DEFAULT_INCREMENT_MS);
    }
}
