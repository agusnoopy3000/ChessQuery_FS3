package cl.chessquery.game.opening;

import cl.chessquery.game.entity.Opening;
import cl.chessquery.game.repository.OpeningRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Detecta la apertura a partir del PGN.
 * Extrae los primeros 10 movimientos (ignorando las cabeceras del PGN)
 * y busca en la base de datos la coincidencia más larga.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpeningDetector {

    private static final Pattern MOVE_NUMBER_PATTERN = Pattern.compile("\\d+\\.");
    private static final Pattern PGN_HEADER_PATTERN  = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);

    private final OpeningRepository openingRepo;

    /**
     * Detecta la apertura del PGN dado.
     * Retorna Optional.empty() si no hay coincidencia.
     */
    public Optional<Opening> detectOpening(String pgn) {
        if (pgn == null || pgn.isBlank()) {
            return Optional.empty();
        }
        String moves = extractMoves(pgn, 10);
        if (moves.isBlank()) {
            return Optional.empty();
        }
        try {
            return openingRepo.findBestMatch(moves);
        } catch (Exception e) {
            log.warn("Error al detectar apertura: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extrae los primeros {@code maxMoves} movimientos del PGN.
     * El formato PGN tiene cabeceras entre corchetes [Event ...] y luego los movimientos.
     * Ejemplo de movimientos: "1. e4 e5 2. Nf3 Nc6 3. Bb5"
     */
    private String extractMoves(String pgn, int maxMoves) {
        // Eliminar cabeceras PGN (líneas con [Tag "Value"])
        String noHeaders = PGN_HEADER_PATTERN.matcher(pgn).replaceAll("").trim();

        // Dividir por números de turno ("1.", "2.", etc.) y tomar los primeros maxMoves pares
        // Primero eliminamos los comentarios { ... }
        String noComments = noHeaders.replaceAll("\\{[^}]*\\}", "").trim();

        // Dividir tokens
        String[] tokens = noComments.split("\\s+");

        // Filtrar tokens de resultado final (1-0, 0-1, 1/2-1/2, *)
        java.util.List<String> moveTokens = new java.util.ArrayList<>();
        int moveCount = 0;

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            if (token.matches("(1-0|0-1|1/2-1/2|\\*)")) break;
            if (MOVE_NUMBER_PATTERN.matcher(token).matches()) continue; // saltar "1.", "2."
            // Es un movimiento
            moveTokens.add(token);
            moveCount++;
            if (moveCount >= maxMoves * 2) break; // maxMoves pares = maxMoves * 2 movimientos individuales
        }

        return String.join(" ", moveTokens);
    }
}
