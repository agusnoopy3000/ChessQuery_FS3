package cl.chessquery.game.service;

import cl.chessquery.game.dto.LiveGameDtos.*;
import cl.chessquery.game.dto.RegisterGameRequest;
import cl.chessquery.game.entity.GameType;
import cl.chessquery.game.entity.LiveGameMove;
import cl.chessquery.game.entity.LiveGameSession;
import cl.chessquery.game.entity.LiveGameSession.SessionStatus;
import cl.chessquery.game.exception.ApiException;
import cl.chessquery.game.opening.OpeningDetector;
import cl.chessquery.game.repository.LiveGameMoveRepository;
import cl.chessquery.game.repository.LiveGameSessionRepository;
import cl.chessquery.game.realtime.LiveGameBroadcaster;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.move.MoveList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveGameService {

    private static final String INITIAL_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final LiveGameSessionRepository sessionRepo;
    private final LiveGameMoveRepository    moveRepo;
    private final LiveGameFinalizer          finalizer;
    private final LiveGameBroadcaster        broadcaster;
    private final OpeningDetector            openingDetector;
    private final EventPublisherService      events;
    private final org.springframework.web.client.RestTemplate restTemplate;
    @org.springframework.beans.factory.annotation.Value("${gateway.ms-users.url:http://ms-users:8081}")
    private String msUsersUrl;

    // ── Invitar a otro jugador a una partida ──────────────────────────────

    /**
     * Invita por email a un jugador a unirse a la partida. Si el email matchea
     * un Player registrado, publica un evento {@code game.invitation} para que
     * ms-notifications cree una notificación in-app (toast push). El email
     * propiamente dicho lo manda el frontend vía Supabase magic link.
     *
     * Devuelve {@code { matched: boolean, playerId?: number }} para que el
     * frontend pueda saber si el destinatario tendrá feedback in-app o solo
     * el correo.
     */
    public java.util.Map<String, Object> invitePlayer(Long sessionId, String email,
                                                       String gameUrl, Long inviterId) {
        LiveGameSession s = findOrThrow(sessionId);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("matched", false);

        if (email == null || email.isBlank()) {
            return result;
        }

        Long invitedPlayerId = null;
        String invitedName = null;
        try {
            String url = msUsersUrl + "/users/by-email?email={email}";
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = restTemplate.getForObject(url, java.util.Map.class, email.trim());
            if (body != null && body.get("id") instanceof Number) {
                invitedPlayerId = ((Number) body.get("id")).longValue();
                invitedName = (body.get("firstName") + " " + body.get("lastName")).trim();
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            log.debug("invite: email {} no tiene Player registrado, solo se enviará magic link", email);
        } catch (Exception ex) {
            log.warn("invite: lookup ms-users falló para email={}: {}", email, ex.getMessage());
        }

        if (invitedPlayerId == null) {
            return result;
        }
        if (invitedPlayerId.equals(inviterId)) {
            log.debug("invite: el invitado es el propio creador, no notifico");
            return result;
        }

        String inviterName = "tu rival";
        if (inviterId != null && inviterId.equals(s.getWhitePlayerId())) {
            inviterName = "el creador de la partida";
        }
        events.publishGameInvitation(sessionId, invitedPlayerId, inviterId, inviterName, gameUrl);
        log.info("invite: game.invitation publicado game={} → player={} ({})",
                sessionId, invitedPlayerId, invitedName);

        result.put("matched", true);
        result.put("playerId", invitedPlayerId);
        return result;
    }

    // ── Crear sesión (creator = white) ─────────────────────────────────────

    @Transactional
    public LiveGameResponse create(CreateLiveGameRequest req) {
        // Modo torneo: si viene blackPlayerId, ambos jugadores quedan asignados y
        // la sesión arranca ACTIVE (no espera join). En casual queda WAITING.
        boolean tournamentMode = req.blackPlayerId() != null;
        if (tournamentMode && req.blackPlayerId().equals(req.whitePlayerId())) {
            throw new ApiException(400, "SAME_PLAYER",
                    "El jugador de blancas y negras no pueden ser el mismo");
        }
        Instant now = Instant.now();
        LiveGameSession s = LiveGameSession.builder()
                .whitePlayerId(req.whitePlayerId())
                .blackPlayerId(req.blackPlayerId())
                .status(tournamentMode ? SessionStatus.ACTIVE : SessionStatus.WAITING)
                .initialFen(INITIAL_FEN)
                .currentFen(INITIAL_FEN)
                .turn("w")
                .timeControlInitialMs(req.timeControlInitialMs())
                .timeControlIncrementMs(req.timeControlIncrementMs())
                .clockWhiteMs(req.timeControlInitialMs())
                .clockBlackMs(req.timeControlInitialMs())
                .whiteEloBefore(req.whiteEloBefore())
                .blackEloBefore(req.blackEloBefore())
                .tournamentPairingId(req.tournamentPairingId())
                .startedAt(tournamentMode ? now : null)
                .build();
        sessionRepo.save(s);
        log.info("LiveGame creada id={} white={} black={} pairing={} modo={}",
                s.getId(), s.getWhitePlayerId(), s.getBlackPlayerId(),
                s.getTournamentPairingId(), tournamentMode ? "torneo" : "casual");
        return toResponse(s, List.of());
    }

    // ── Obtener (con jugadas) ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LiveGameResponse get(Long id) {
        LiveGameSession s = findOrThrow(id);
        List<LiveGameMove> moves = moveRepo.findBySessionIdOrderByCreatedAtAsc(id);
        return toResponse(s, moves);
    }

    // ── Join (rival = black) ───────────────────────────────────────────────

    @Transactional
    public LiveGameResponse join(Long id, JoinLiveGameRequest req) {
        LiveGameSession s = findOrThrow(id);
        if (s.getStatus() != SessionStatus.WAITING) {
            if (s.getStatus() == SessionStatus.ACTIVE && req.playerId().equals(s.getBlackPlayerId())) {
                return toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));
            }
            throw new ApiException(409, "SESSION_NOT_WAITING",
                    "La sesión no está en estado WAITING");
        }
        if (req.playerId().equals(s.getWhitePlayerId())) {
            throw new ApiException(400, "SAME_PLAYER",
                    "El creador y el rival no pueden ser el mismo jugador");
        }
        Instant now = Instant.now();
        s.setBlackPlayerId(req.playerId());
        s.setBlackEloBefore(req.eloBefore());
        s.setStatus(SessionStatus.ACTIVE);
        s.setStartedAt(now);
        sessionRepo.save(s);
        log.info("LiveGame {} joined: black={}", id, req.playerId());
        LiveGameResponse response = toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));
        Map<String, Object> startedPayload = new java.util.HashMap<>();
        startedPayload.put("blackPlayerId", req.playerId());
        startedPayload.put("startedAt", now.toString());
        startedPayload.put("state", response);
        broadcaster.publish(id, "game.started", startedPayload);
        return response;
    }

    // ── Move ───────────────────────────────────────────────────────────────

    @Transactional
    public LiveGameResponse move(Long id, MoveRequest req) {
        // Lock pesimista: serializa moves concurrentes sobre la misma sesión
        // (evita duplicate-key cuando llegan 2 requests en paralelo).
        LiveGameSession s = findForUpdateOrThrow(id);
        LiveGameResponse idempotentRetry = idempotentMoveRetry(s, req);
        if (idempotentRetry != null) {
            return idempotentRetry;
        }
        if (s.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(409, "SESSION_NOT_ACTIVE",
                    "La sesión no está activa (estado=" + s.getStatus() + ")");
        }
        // Verificar turno
        Side expected = "w".equals(s.getTurn()) ? Side.WHITE : Side.BLACK;
        Long expectedPlayerId = expected == Side.WHITE ? s.getWhitePlayerId() : s.getBlackPlayerId();
        if (!req.playerId().equals(expectedPlayerId)) {
            throw new ApiException(403, "NOT_YOUR_TURN",
                    "No es el turno del jugador " + req.playerId());
        }

        // Validar y aplicar jugada con chesslib
        Board board = new Board();
        board.loadFromFen(s.getCurrentFen());
        Move move;
        try {
            move = new Move(req.uci(), board.getSideToMove());
        } catch (Exception e) {
            throw new ApiException(400, "INVALID_UCI",
                    "UCI inválido: " + req.uci());
        }
        if (!board.legalMoves().contains(move)) {
            throw new ApiException(400, "ILLEGAL_MOVE",
                    "Jugada ilegal en la posición actual");
        }
        String san = sanForMove(board, move);
        board.doMove(move);

        // Persistir jugada
        int totalPrev = (int) moveRepo.countBySessionId(id);
        int moveNumber = (totalPrev / 2) + 1;
        String color = expected == Side.WHITE ? "w" : "b";
        LiveGameMove lm = LiveGameMove.builder()
                .sessionId(id)
                .moveNumber(moveNumber)
                .playerId(req.playerId())
                .color(color)
                .uci(req.uci())
                .san(san)
                .fenAfter(board.getFen())
                .clockWhiteMs(req.clockWhiteMs())
                .clockBlackMs(req.clockBlackMs())
                .build();
        moveRepo.save(lm);

        // Actualizar sesión
        Instant now = Instant.now();
        s.setCurrentFen(board.getFen());
        s.setTurn(board.getSideToMove() == Side.WHITE ? "w" : "b");
        s.setLastMoveAt(now);
        if (req.clockWhiteMs() != null) s.setClockWhiteMs(req.clockWhiteMs());
        if (req.clockBlackMs() != null) s.setClockBlackMs(req.clockBlackMs());

        // Detectar fin
        String terminal = detectTerminal(board);
        if (terminal != null) {
            String result = switch (terminal) {
                case "CHECKMATE" -> color.equals("w") ? "1-0" : "0-1";
                default -> "1/2-1/2";
            };
            finishSession(s, result, terminal, now);
        }
        sessionRepo.save(s);

        // Construir response una sola vez para devolver Y broadcastear.
        // Enviar el estado completo en el broadcast permite al rival actualizar
        // su tablero sin un GET adicional (elimina ~50-200ms de round-trip).
        LiveGameResponse response = toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));

        Map<String, Object> movePayload = new java.util.HashMap<>();
        movePayload.put("moveNumber", moveNumber);
        movePayload.put("color", color);
        movePayload.put("uci", req.uci());
        movePayload.put("san", san);
        movePayload.put("fenAfter", board.getFen());
        movePayload.put("clockWhiteMs", req.clockWhiteMs() == null ? -1L : req.clockWhiteMs());
        movePayload.put("clockBlackMs", req.clockBlackMs() == null ? -1L : req.clockBlackMs());
        movePayload.put("state", response);
        broadcaster.publish(id, "move.played", movePayload);
        if (terminal != null) {
            broadcastFinished(s);
        }

        return response;
    }

    // ── Rematch ────────────────────────────────────────────────────────────

    /**
     * Crea una nueva sesión con colores invertidos a partir de una sesión
     * FINISHED. Convención: el original.blackPlayerId pasa a ser white en
     * la nueva (color swap). Status WAITING. El rival original se entera
     * vía broadcast 'game.rematch.created' en el canal de la sesión vieja.
     */
    @Transactional
    public LiveGameResponse rematch(Long id, RematchRequest req) {
        LiveGameSession original = findOrThrow(id);
        if (original.getStatus() != SessionStatus.FINISHED) {
            throw new ApiException(409, "SESSION_NOT_FINISHED",
                    "Solo se puede pedir revancha de una partida finalizada");
        }
        if (original.getBlackPlayerId() == null) {
            throw new ApiException(400, "NO_OPPONENT",
                    "No hay rival con quien hacer revancha");
        }
        boolean isParticipant = req.playerId().equals(original.getWhitePlayerId())
                || req.playerId().equals(original.getBlackPlayerId());
        if (!isParticipant) {
            throw new ApiException(403, "NOT_A_PLAYER",
                    "Solo los participantes pueden pedir revancha");
        }

        // Color swap: el black original pasa a ser white en la nueva.
        LiveGameSession s = LiveGameSession.builder()
                .whitePlayerId(original.getBlackPlayerId())
                .status(SessionStatus.WAITING)
                .initialFen(INITIAL_FEN)
                .currentFen(INITIAL_FEN)
                .turn("w")
                .timeControlInitialMs(original.getTimeControlInitialMs())
                .timeControlIncrementMs(original.getTimeControlIncrementMs())
                .clockWhiteMs(original.getTimeControlInitialMs())
                .clockBlackMs(original.getTimeControlInitialMs())
                .whiteEloBefore(original.getBlackEloBefore())
                .build();
        sessionRepo.save(s);
        log.info("LiveGame {} rematch creado como {} (whiteWasBlack={})",
                id, s.getId(), original.getBlackPlayerId());

        // Notificar a quien sigue suscrito al canal viejo (modal abierto).
        Map<String, Object> rematchPayload = new java.util.HashMap<>();
        rematchPayload.put("newSessionId", s.getId());
        rematchPayload.put("requestedBy", req.playerId());
        broadcaster.publish(id, "game.rematch.created", rematchPayload);

        return toResponse(s, List.of());
    }

    // ── Resign ─────────────────────────────────────────────────────────────

    @Transactional
    public LiveGameResponse resign(Long id, ResignRequest req) {
        LiveGameSession s = findOrThrow(id);
        LiveGameResponse idempotentRetry = idempotentFinishedRetry(s, req.playerId(), "RESIGN");
        if (idempotentRetry != null) {
            return idempotentRetry;
        }
        if (s.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(409, "SESSION_NOT_ACTIVE",
                    "La sesión no está activa");
        }
        boolean whiteResigns = req.playerId().equals(s.getWhitePlayerId());
        boolean blackResigns = req.playerId().equals(s.getBlackPlayerId());
        if (!whiteResigns && !blackResigns) {
            throw new ApiException(403, "NOT_A_PLAYER",
                    "El jugador " + req.playerId() + " no participa en esta partida");
        }
        String result = whiteResigns ? "0-1" : "1-0";
        Instant now = Instant.now();
        finishSession(s, result, "RESIGN", now);
        sessionRepo.save(s);
        broadcastFinished(s);
        return toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));
    }

    // ── Timeout / pérdida por tiempo (R5) ─────────────────────────────────

    /**
     * Cierra la partida cuando el reloj de un jugador llega a 0. El cliente
     * detecta el time-out y notifica; el servidor registra al oponente como
     * ganador con endReason TIMEOUT.
     */
    @Transactional
    public LiveGameResponse timeout(Long id, ResignRequest req) {
        LiveGameSession s = findOrThrow(id);
        LiveGameResponse idempotentRetry = idempotentFinishedRetry(s, req.playerId(), "TIMEOUT");
        if (idempotentRetry != null) {
            return idempotentRetry;
        }
        if (s.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(409, "SESSION_NOT_ACTIVE",
                    "La sesión no está activa");
        }
        boolean whiteFlagged = req.playerId().equals(s.getWhitePlayerId());
        boolean blackFlagged = req.playerId().equals(s.getBlackPlayerId());
        if (!whiteFlagged && !blackFlagged) {
            throw new ApiException(403, "NOT_A_PLAYER",
                    "El jugador " + req.playerId() + " no participa en esta partida");
        }
        // El que se quedó sin tiempo pierde; el oponente gana.
        String result = whiteFlagged ? "0-1" : "1-0";
        Instant now = Instant.now();
        // Forzamos el reloj a 0 para reflejar el evento.
        if (whiteFlagged) s.setClockWhiteMs(0L);
        else s.setClockBlackMs(0L);
        finishSession(s, result, "TIMEOUT", now);
        sessionRepo.save(s);
        broadcastFinished(s);
        return toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));
    }

    // ── Draw by agreement (R11) ───────────────────────────────────────────

    /**
     * Cierra la partida con resultado 1/2-1/2 cuando ambos jugadores acuerdan
     * tablas. Solo participa quien está jugando; la negociación de la oferta
     * la hacen los clientes vía Realtime broadcast (draw.offered/accepted/rejected).
     */
    @Transactional
    public LiveGameResponse drawAgreement(Long id, ResignRequest req) {
        LiveGameSession s = findOrThrow(id);
        LiveGameResponse idempotentRetry = idempotentFinishedRetry(s, req.playerId(), "DRAW_AGREEMENT");
        if (idempotentRetry != null) {
            return idempotentRetry;
        }
        if (s.getStatus() != SessionStatus.ACTIVE) {
            throw new ApiException(409, "SESSION_NOT_ACTIVE",
                    "La sesión no está activa");
        }
        if (!req.playerId().equals(s.getWhitePlayerId())
                && !req.playerId().equals(s.getBlackPlayerId())) {
            throw new ApiException(403, "NOT_A_PLAYER",
                    "El jugador " + req.playerId() + " no participa en esta partida");
        }
        Instant now = Instant.now();
        finishSession(s, "1/2-1/2", "DRAW_AGREEMENT", now);
        sessionRepo.save(s);
        broadcastFinished(s);
        return toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(id));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private LiveGameSession findOrThrow(Long id) {
        return sessionRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "SESSION_NOT_FOUND",
                        "Sesión live " + id + " no encontrada"));
    }

    /** Igual que findOrThrow pero adquiere lock pesimista (SELECT ... FOR UPDATE)
     *  para serializar moves concurrentes sobre la misma sesion. */
    private LiveGameSession findForUpdateOrThrow(Long id) {
        return sessionRepo.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(404, "SESSION_NOT_FOUND",
                        "Sesión live " + id + " no encontrada"));
    }

    private LiveGameResponse idempotentMoveRetry(LiveGameSession s, MoveRequest req) {
        return moveRepo.findTopBySessionIdOrderByCreatedAtDesc(s.getId())
                .filter(last -> req.playerId().equals(last.getPlayerId()))
                .filter(last -> req.uci().equals(last.getUci()))
                .filter(last -> last.getFenAfter().equals(s.getCurrentFen()))
                .map(last -> toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(s.getId())))
                .orElse(null);
    }

    private LiveGameResponse idempotentFinishedRetry(LiveGameSession s, Long playerId, String reason) {
        if (s.getStatus() != SessionStatus.FINISHED || !reason.equals(s.getEndReason())) {
            return null;
        }
        boolean participant = playerId.equals(s.getWhitePlayerId()) || playerId.equals(s.getBlackPlayerId());
        if (!participant) {
            return null;
        }
        return toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(s.getId()));
    }

    /** Marca FINISHED y agenda la materialización async en `game` con PGN. */
    private void finishSession(LiveGameSession s, String result, String reason, Instant now) {
        s.setStatus(SessionStatus.FINISHED);
        s.setResult(result);
        s.setEndReason(reason);
        s.setFinishedAt(now);

        if (s.getBlackPlayerId() == null) {
            log.warn("LiveGame {} finalizada sin oponente; no se materializa game", s.getId());
            return;
        }

        // Construir PGN desde la lista de jugadas
        List<LiveGameMove> moves = moveRepo.findBySessionIdOrderByCreatedAtAsc(s.getId());
        String pgn = buildPgn(s, moves, result);

        int duration = s.getStartedAt() != null
                ? (int) Math.max(0, java.time.Duration.between(s.getStartedAt(), now).toSeconds())
                : 0;
        finalizer.scheduleAfterCommit(s.getId(), new RegisterGameRequest(
                s.getWhitePlayerId(),
                s.getBlackPlayerId(),
                result,
                GameType.CASUAL,
                s.getWhiteEloBefore(),
                s.getBlackEloBefore(),
                moves.size(),
                s.getTournamentPairingId(),
                duration,
                s.getStartedAt() != null ? s.getStartedAt() : now,
                pgn
        ));
        log.info("LiveGame {} finalizada (result={} reason={}); materialización PGN programada",
                s.getId(), result, reason);
    }

    private void broadcastFinished(LiveGameSession s) {
        LiveGameResponse response = toResponse(s, moveRepo.findBySessionIdOrderByCreatedAtAsc(s.getId()));
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("result", s.getResult() == null ? "" : s.getResult());
        payload.put("endReason", s.getEndReason() == null ? "" : s.getEndReason());
        payload.put("finalizedGameId", s.getFinalizedGameId() == null ? -1L : s.getFinalizedGameId());
        payload.put("state", response);
        broadcaster.publish(s.getId(), "game.finished", payload);
    }

    /** Detecta CHECKMATE / STALEMATE / DRAW (50-move, repetition, insuficiente). */
    private String detectTerminal(Board board) {
        if (board.isMated()) return "CHECKMATE";
        if (board.isStaleMate()) return "STALEMATE";
        if (board.isInsufficientMaterial()) return "DRAW_INSUFFICIENT";
        if (board.isRepetition()) return "DRAW_REPETITION";
        if (board.getHalfMoveCounter() >= 100) return "DRAW_50MOVE";
        return null;
    }

    /** SAN de una jugada legal sobre la posición actual del board (sin aplicarla). */
    private String sanForMove(Board board, Move move) {
        Board copy = board.clone();
        MoveList ml = new MoveList(copy.getFen());
        ml.add(move);
        try {
            return ml.toSanArray()[0];
        } catch (Exception e) {
            return move.toString();
        }
    }

    /** PGN mínimo válido a partir de los movimientos persistidos. */
    private String buildPgn(LiveGameSession s, List<LiveGameMove> moves, String result) {
        StringBuilder sb = new StringBuilder();
        // Seven Tag Roster (orden requerido por PGN spec): Event, Site, Date, Round, White, Black, Result.
        sb.append("[Event \"ChessQuery Live\"]\n");
        sb.append("[Site \"chessquery.local\"]\n");
        sb.append("[Date \"").append(Instant.now().toString().substring(0, 10).replace('-', '.')).append("\"]\n");
        sb.append("[Round \"-\"]\n");
        sb.append("[White \"Player ").append(s.getWhitePlayerId()).append("\"]\n");
        sb.append("[Black \"Player ").append(s.getBlackPlayerId()).append("\"]\n");
        sb.append("[Result \"").append(result).append("\"]\n");
        // Tags adicionales útiles para parsers (lichess los reconoce).
        if (s.getEndReason() != null) {
            sb.append("[Termination \"").append(terminationLabel(s.getEndReason())).append("\"]\n");
        }
        if (s.getTimeControlInitialMs() != null) {
            long base = s.getTimeControlInitialMs() / 1000;
            long inc = s.getTimeControlIncrementMs() == null ? 0 : s.getTimeControlIncrementMs() / 1000;
            sb.append("[TimeControl \"").append(base).append('+').append(inc).append("\"]\n");
        }
        sb.append('\n');

        for (LiveGameMove m : moves) {
            if ("w".equals(m.getColor())) {
                sb.append(m.getMoveNumber()).append(". ");
            }
            sb.append(m.getSan()).append(' ');
        }
        sb.append(result).append('\n');
        return sb.toString();
    }

    private String terminationLabel(String endReason) {
        return switch (endReason) {
            case "CHECKMATE" -> "Normal";
            case "RESIGN" -> "Abandoned";
            case "STALEMATE" -> "Stalemate";
            case "DRAW_INSUFFICIENT" -> "Insufficient material";
            case "DRAW_REPETITION" -> "Threefold repetition";
            case "DRAW_50MOVE" -> "Fifty-move rule";
            case "DRAW_AGREEMENT" -> "Agreed";
            case "TIMEOUT" -> "Time forfeit";
            default -> "Unterminated";
        };
    }

    private LiveGameResponse toResponse(LiveGameSession s, List<LiveGameMove> moves) {
        List<LiveMoveResponse> mr = moves.stream()
                .map(m -> new LiveMoveResponse(
                        m.getMoveNumber(), m.getColor(), m.getUci(), m.getSan(),
                        m.getFenAfter(), m.getClockWhiteMs(), m.getClockBlackMs(),
                        m.getCreatedAt()))
                .toList();
        String openingEco = null;
        String openingName = null;
        if (moves.size() >= 2 && moves.size() <= 30) {
            // Solo detectamos durante apertura (jugadas 2-15). Después no aporta.
            String sanLine = moves.stream().map(LiveGameMove::getSan)
                    .collect(java.util.stream.Collectors.joining(" "));
            try {
                var op = openingDetector.detectOpening(sanLine);
                if (op.isPresent()) {
                    openingEco = op.get().getEcoCode();
                    openingName = op.get().getName();
                }
            } catch (Exception e) {
                log.debug("No se pudo detectar apertura inline: {}", e.getMessage());
            }
        }
        return new LiveGameResponse(
                s.getId(),
                s.getWhitePlayerId(),
                s.getBlackPlayerId(),
                s.getStatus().name(),
                s.getCurrentFen(),
                s.getTurn(),
                s.getResult(),
                s.getEndReason(),
                s.getTimeControlInitialMs(),
                s.getTimeControlIncrementMs(),
                s.getClockWhiteMs(),
                s.getClockBlackMs(),
                s.getFinalizedGameId(),
                mr,
                s.getStartedAt(),
                s.getFinishedAt(),
                s.getLastMoveAt(),
                openingEco,
                openingName,
                s.getTournamentPairingId()
        );
    }
}
