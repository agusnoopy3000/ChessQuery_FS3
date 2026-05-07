import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Chessground } from 'chessground';
import type { Api as CgApi } from 'chessground/api';
import type { Config as CgConfig } from 'chessground/config';
import type { Key } from 'chessground/types';
import { Chess } from 'chessops/chess';
import { parseFen, makeFen } from 'chessops/fen';
import { parseSquare, parseUci } from 'chessops/util';
import { chessgroundDests } from 'chessops/compat';
import { useAuth } from '@chessquery/shared';
import { Button, Card } from '@chessquery/ui-lib';
import { api, playerApi } from '../api';
import { supabase } from '../lib/supabase';
import { useMyPlayerId } from '../hooks/useMyPlayerId';

import 'chessground/assets/chessground.base.css';
import 'chessground/assets/chessground.brown.css';
import 'chessground/assets/chessground.cburnett.css';

interface LiveMove {
  moveNumber: number;
  color: 'w' | 'b';
  uci: string;
  san: string;
  fenAfter: string;
}

// Sonidos de jugada (assets en /sounds/, set estándar de lichess)
const sounds = {
  move: typeof Audio !== 'undefined' ? new Audio('/sounds/move.mp3') : null,
  capture: typeof Audio !== 'undefined' ? new Audio('/sounds/capture.mp3') : null,
  check: typeof Audio !== 'undefined' ? new Audio('/sounds/check.mp3') : null,
  notify: typeof Audio !== 'undefined' ? new Audio('/sounds/genericnotify.mp3') : null,
};
const playSound = (kind: keyof typeof sounds) => {
  const a = sounds[kind];
  if (!a) return;
  a.currentTime = 0;
  a.play().catch(() => { /* autoplay bloqueado: ignorar silenciosamente */ });
};

interface LiveGameState {
  id: number;
  whitePlayerId: number;
  blackPlayerId: number | null;
  status: 'WAITING' | 'ACTIVE' | 'FINISHED' | 'ABANDONED';
  currentFen: string;
  turn: 'w' | 'b';
  result: string | null;
  endReason: string | null;
  finalizedGameId: number | null;
  moves: LiveMove[];
  startedAt: string | null;
  finishedAt: string | null;
}

const dataApi = {
  get: (id: string) => api.get<LiveGameState>(`/api/player/play/live/${id}`).then((r) => r.data),
  join: (id: string, eloBefore?: number) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/join`, { eloBefore }).then((r) => r.data),
  move: (id: string, uci: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/move`, { uci }).then((r) => r.data),
  resign: (id: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/resign`).then((r) => r.data),
};

export const LiveGamePage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const cgRef = useRef<HTMLDivElement>(null);
  const cgApi = useRef<CgApi | null>(null);
  const [state, setState] = useState<LiveGameState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const myPlayerId = useMyPlayerId();
  const [whiteName, setWhiteName] = useState<string>('Blancas');
  const [blackName, setBlackName] = useState<string>('Negras');
  const [confirmingResign, setConfirmingResign] = useState(false);
  const [copied, setCopied] = useState(false);
  const lastMoveCountRef = useRef(0);
  const prevStatusRef = useRef<string | null>(null);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteSent, setInviteSent] = useState(false);
  const [inviteSending, setInviteSending] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [opponentOnline, setOpponentOnline] = useState(false);

  // Resuelve nombres de los jugadores cuando cambian los IDs.
  useEffect(() => {
    if (!state) return;
    const fetchName = async (pid: number | null): Promise<string> => {
      if (pid == null) return '— esperando rival —';
      try {
        const p = await playerApi.publicProfile(pid);
        const n = `${p.profile?.firstName ?? ''} ${p.profile?.lastName ?? ''}`.trim();
        return n || `Jugador ${pid}`;
      } catch {
        return `Jugador ${pid}`;
      }
    };
    let cancelled = false;
    Promise.all([fetchName(state.whitePlayerId), fetchName(state.blackPlayerId)])
      .then(([w, b]) => { if (!cancelled) { setWhiteName(w); setBlackName(b); } });
    return () => { cancelled = true; };
  }, [state?.whitePlayerId, state?.blackPlayerId]);

  // Determinar el color del usuario actual usando el player.id resuelto
  const myColor: 'white' | 'black' | null = useMemo(() => {
    if (!state || myPlayerId == null) return null;
    if (myPlayerId === state.whitePlayerId) return 'white';
    if (myPlayerId === state.blackPlayerId) return 'black';
    return null;
  }, [state, myPlayerId]);

  // Carga inicial
  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    dataApi
      .get(id)
      .then((s) => { if (!cancelled) setState(s); })
      .catch((e) => { if (!cancelled) setError(message(e)); });
    return () => { cancelled = true; };
  }, [id]);

  // Subscripción a Supabase Realtime + presencia.
  // Backend envía el estado completo en `payload.state`, así que aplicamos
  // directo sin GET extra (~50-200ms menos de latencia entre jugadores).
  useEffect(() => {
    if (!id) return;
    const applyOrFetch = (payload: { state?: LiveGameState } | undefined) => {
      if (payload?.state) {
        setState(payload.state);
      } else {
        dataApi.get(id).then(setState).catch(() => {});
      }
    };
    const channel = supabase.channel(`game:${id}`, {
      config: {
        broadcast: { self: false },
        presence: { key: myPlayerId != null ? String(myPlayerId) : undefined },
      },
    });
    const refreshOpponent = () => {
      if (myPlayerId == null || !state) { setOpponentOnline(false); return; }
      const opponentId = myPlayerId === state.whitePlayerId ? state.blackPlayerId : state.whitePlayerId;
      if (opponentId == null) { setOpponentOnline(false); return; }
      const presenceState = channel.presenceState() as Record<string, unknown>;
      setOpponentOnline(String(opponentId) in presenceState);
    };
    channel
      .on('broadcast', { event: 'move.played' }, ({ payload }) => applyOrFetch(payload))
      .on('broadcast', { event: 'game.started' }, ({ payload }) => applyOrFetch(payload))
      .on('broadcast', { event: 'game.finished' }, ({ payload }) => applyOrFetch(payload))
      .on('presence', { event: 'sync' }, refreshOpponent)
      .on('presence', { event: 'join' }, refreshOpponent)
      .on('presence', { event: 'leave' }, refreshOpponent)
      .subscribe(async (status) => {
        if (status === 'SUBSCRIBED' && myPlayerId != null) {
          await channel.track({ playerId: myPlayerId, online_at: new Date().toISOString() });
        }
      });
    return () => { supabase.removeChannel(channel); };
  }, [id, myPlayerId, state?.whitePlayerId, state?.blackPlayerId]);

  // Rehidratación al volver del background (visibilitychange + reconnect).
  // Resuelve "se quedó pegado" cuando el OS suspendió la pestaña.
  useEffect(() => {
    if (!id) return;
    const refresh = () => {
      if (document.visibilityState === 'visible') {
        dataApi.get(id).then(setState).catch(() => {});
      }
    };
    document.addEventListener('visibilitychange', refresh);
    window.addEventListener('online', refresh);
    return () => {
      document.removeEventListener('visibilitychange', refresh);
      window.removeEventListener('online', refresh);
    };
  }, [id]);

  // Sonidos en cambios de estado (jugada nueva, inicio, fin).
  useEffect(() => {
    if (!state) return;
    const prev = lastMoveCountRef.current;
    const curr = state.moves.length;
    if (curr > prev) {
      const last = state.moves[curr - 1];
      const inCheck = chessFromFen(state.currentFen)?.isCheck();
      if (inCheck) playSound('check');
      else if (last.san.includes('x')) playSound('capture');
      else playSound('move');
    }
    lastMoveCountRef.current = curr;
    if (state.status === 'FINISHED' && state.finishedAt) playSound('notify');
    if (prevStatusRef.current === 'WAITING' && state.status === 'ACTIVE') playSound('notify');
    prevStatusRef.current = state.status;
  }, [state?.moves.length, state?.status]);

  // Auto-join si soy el rival y la sesión está WAITING.
  // Guard con ref: solo intentamos join una vez por id de sesión, sin
  // depender del valor numérico de user.id (que la app no resuelve aún).
  const joinAttempted = useRef<string | null>(null);
  useEffect(() => {
    if (!id || !state || !user) return;
    if (state.status !== 'WAITING') return;
    if (state.blackPlayerId != null) return; // ya hay rival
    if (joinAttempted.current === id) return; // ya intentamos este id
    joinAttempted.current = id;
    dataApi.join(id).then(setState).catch((e) => setError(message(e)));
  }, [id, state, user]);

  // Render del tablero
  useEffect(() => {
    if (!cgRef.current || !state) return;

    const chess = chessFromFen(state.currentFen);
    const dests = chess ? chessgroundDests(chess) : new Map();
    const turnColor: 'white' | 'black' = state.turn === 'w' ? 'white' : 'black';
    const isMyTurn = myColor !== null && turnColor === myColor && state.status === 'ACTIVE';

    const config: CgConfig = {
      fen: state.currentFen,
      turnColor,
      orientation: myColor ?? 'white',
      check: chess?.isCheck() ? turnColor : undefined,
      movable: {
        free: false,
        color: isMyTurn ? myColor! : undefined,
        dests: isMyTurn ? dests : new Map(),
        showDests: true,
        events: {
          after: (orig: Key, dest: Key) => {
            handleMove(orig, dest, chess);
          },
        },
      },
      premovable: { enabled: false },
      draggable: { enabled: isMyTurn },
      animation: { enabled: true, duration: 200 },
      highlight: { lastMove: true, check: true },
    };

    if (cgApi.current) {
      cgApi.current.set(config);
    } else {
      cgApi.current = Chessground(cgRef.current, config);
    }
  }, [state, myColor]);

  // Cleanup chessground al desmontar
  useEffect(() => () => { cgApi.current?.destroy?.(); cgApi.current = null; }, []);

  const handleMove = async (orig: Key, dest: Key, chess: Chess | null) => {
    if (!id || !chess || !state) return;
    let uci = `${orig}${dest}`;
    const movePromotion = inferPromotion(orig, dest, chess);
    if (movePromotion) uci += movePromotion;

    // Optimistic UI: aplicamos la jugada localmente con chessops antes de
    // que el backend confirme. Si rechaza, revertimos al FEN previo.
    const fenBeforeMove = state.currentFen;
    try {
      const moveObj = parseUci(uci);
      if (moveObj) {
        const optimisticChess = chess.clone();
        optimisticChess.play(moveObj);
        const optimisticFen = makeFen(optimisticChess.toSetup());
        cgApi.current?.set({
          fen: optimisticFen,
          turnColor: state.turn === 'w' ? 'black' : 'white',
          movable: { color: undefined, dests: new Map() },
        });
      }
    } catch { /* errores client-side los maneja el server */ }

    setBusy(true);
    try {
      const next = await dataApi.move(id, uci);
      setState(next);
    } catch (e) {
      setError(message(e));
      cgApi.current?.set({ fen: fenBeforeMove });
    } finally {
      setBusy(false);
    }
  };

  // Patrón two-click: el primer click pide confirmación inline; el segundo
  // ejecuta. Reemplaza el `confirm()` nativo intrusivo por un toggle de UI.
  const handleResign = async () => {
    if (!id) return;
    if (!confirmingResign) {
      setConfirmingResign(true);
      setTimeout(() => setConfirmingResign(false), 4000);
      return;
    }
    setConfirmingResign(false);
    setBusy(true);
    try {
      const next = await dataApi.resign(id);
      setState(next);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };

  if (error && !state) return <div className="page-shell"><Card><p>Error: {error}</p></Card></div>;
  if (!state) return <div className="page-shell"><p>Cargando partida…</p></div>;

  const shareUrl = typeof window !== 'undefined' ? `${window.location.origin}/play/${state.id}` : '';

  const opponentName = myColor === 'white' ? blackName : whiteName;
  const topName = myColor === 'black' ? whiteName : blackName;
  const bottomName = myColor === 'black' ? blackName : whiteName;
  const turnLabel = state.turn === 'w' ? whiteName : blackName;

  return (
    <div
      className="page-shell"
      style={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) 340px',
        gap: 24,
        padding: 24,
        maxWidth: 1100,
        margin: '0 auto',
        alignItems: 'start',
      }}
    >
      {/* Columna del tablero — centrado vertical y horizontal */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
        <div style={{ alignSelf: 'flex-start', fontSize: 14, fontWeight: 600 }}>
          {myColor === 'black' ? '⚪' : '⚫'} {topName}
          {myColor && state.status === 'ACTIVE' && (
            <span
              title={opponentOnline ? 'Conectado' : 'Desconectado'}
              style={{ marginLeft: 8, fontSize: 10 }}
            >
              {opponentOnline ? '🟢' : '🔴'}
            </span>
          )}
        </div>

        <div
          ref={cgRef}
          style={{
            width: 'min(560px, 90vw)',
            aspectRatio: '1 / 1',
          }}
        />

        <div style={{ alignSelf: 'flex-start', fontSize: 14, fontWeight: 600 }}>
          {myColor === 'black' ? '⚫' : '⚪'} {bottomName} {myColor && '(tú)'}
        </div>

        {state.status === 'ACTIVE' && myColor && (
          <Button
            onClick={handleResign}
            disabled={busy}
            variant="danger"
            style={{ marginTop: 8 }}
          >
            {confirmingResign ? '¿Seguro? Click para confirmar' : 'Rendirse'}
          </Button>
        )}
      </div>

      {/* Panel lateral */}
      <Card>
        <h2 style={{ marginTop: 0 }}>Partida #{state.id}</h2>
        <p style={{ fontSize: 14, color: 'var(--text-muted)', marginBottom: 12 }}>
          {state.status === 'ACTIVE' && <>Turno de <strong>{turnLabel}</strong></>}
          {state.status === 'WAITING' && <>Esperando rival…</>}
          {state.status === 'FINISHED' && (
            <>
              Resultado: <strong>{state.result}</strong>
              {state.endReason && <> · {state.endReason}</>}
            </>
          )}
        </p>

        <div style={{ display: 'grid', gap: 6, fontSize: 14, marginBottom: 16 }}>
          <div>⚪ <strong>{whiteName}</strong>{myColor === 'white' && ' (tú)'}</div>
          <div>⚫ <strong>{blackName}</strong>{myColor === 'black' && ' (tú)'}</div>
          {myColor && state.status === 'ACTIVE' && (
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              Rival: {opponentName}
            </div>
          )}
        </div>

        {state.status === 'WAITING' && myColor === 'white' && (
          <Card style={{ marginBottom: 16, background: 'var(--surface-2)' }}>
            <p style={{ margin: 0, fontSize: 13, fontWeight: 600 }}>
              📧 Invitar por email
            </p>
            <p style={{ margin: '4px 0 8px', fontSize: 12, color: 'var(--text-muted)' }}>
              Mandamos un link mágico al rival. Hace click y entra directo a la partida.
            </p>
            <div style={{ display: 'flex', gap: 6, alignItems: 'stretch', marginBottom: 10 }}>
              <input
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="rival@ejemplo.cl"
                disabled={inviteSending || inviteSent}
                style={{
                  flex: 1,
                  background: 'var(--input-bg, #0e100d)',
                  border: '1px solid var(--border, #2a2d27)',
                  borderRadius: 6,
                  padding: '8px 10px',
                  fontSize: 13,
                  color: 'var(--text, #e8ead4)',
                }}
              />
              <Button
                size="sm"
                onClick={async () => {
                  setInviteError(null);
                  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(inviteEmail)) {
                    setInviteError('Email inválido');
                    return;
                  }
                  setInviteSending(true);
                  try {
                    const { error: otpErr } = await supabase.auth.signInWithOtp({
                      email: inviteEmail,
                      options: { emailRedirectTo: shareUrl },
                    });
                    if (otpErr) throw otpErr;
                    setInviteSent(true);
                  } catch (e) {
                    setInviteError(message(e));
                  } finally {
                    setInviteSending(false);
                  }
                }}
                disabled={inviteSending || inviteSent}
              >
                {inviteSent ? '✓ Enviado' : inviteSending ? 'Enviando…' : 'Invitar'}
              </Button>
            </div>
            {inviteError && (
              <p style={{ margin: '0 0 8px', color: 'crimson', fontSize: 12 }}>{inviteError}</p>
            )}
            <p style={{ margin: '0 0 6px', fontSize: 12, color: 'var(--text-muted)' }}>
              O compartí el link directo:
            </p>
            <div style={{ display: 'flex', gap: 6, alignItems: 'stretch' }}>
              <input
                readOnly
                value={shareUrl}
                onClick={(e) => (e.currentTarget as HTMLInputElement).select()}
                style={{
                  flex: 1,
                  background: 'var(--input-bg, #0e100d)',
                  border: '1px solid var(--border, #2a2d27)',
                  borderRadius: 6,
                  padding: '8px 10px',
                  fontSize: 11,
                  fontFamily: 'monospace',
                  color: 'var(--text, #e8ead4)',
                }}
              />
              <Button
                size="sm"
                onClick={async () => {
                  try {
                    await navigator.clipboard.writeText(shareUrl);
                    setCopied(true);
                    setTimeout(() => setCopied(false), 1500);
                  } catch {
                    /* portapapeles bloqueado: el input ya está seleccionado */
                  }
                }}
              >
                {copied ? '✓ Copiado' : 'Copiar'}
              </Button>
            </div>
          </Card>
        )}

        <h3 style={{ margin: '16px 0 8px' }}>Jugadas</h3>
        {state.moves.length === 0 ? (
          <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>Aún no hay jugadas.</p>
        ) : (
          <ol style={{ fontFamily: 'monospace', fontSize: 13, maxHeight: 280, overflow: 'auto', paddingLeft: 22, margin: 0 }}>
            {groupMoves(state.moves).map((pair, i) => (
              <li key={i}>{pair.white} {pair.black ?? ''}</li>
            ))}
          </ol>
        )}

        {state.finalizedGameId && (
          <Link to={`/player/${myPlayerId ?? ''}`} style={{ marginTop: 12, display: 'inline-block' }}>
            Partida guardada como #{state.finalizedGameId} →
          </Link>
        )}

        {error && state.status === 'ACTIVE' && (
          <p style={{ color: 'crimson', marginTop: 12, fontSize: 13 }}>{error}</p>
        )}
      </Card>

      {/* Modal fin de partida — overlay con resultado y CTAs */}
      {state.status === 'FINISHED' && (
        <GameOverModal
          state={state}
          myColor={myColor}
          whiteName={whiteName}
          blackName={blackName}
          onClose={() => navigate('/play')}
          onViewSaved={state.finalizedGameId ? () => navigate(`/player/${myPlayerId ?? ''}`) : null}
        />
      )}
    </div>
  );
};

interface GameOverModalProps {
  state: LiveGameState;
  myColor: 'white' | 'black' | null;
  whiteName: string;
  blackName: string;
  onClose: () => void;
  onViewSaved: (() => void) | null;
}

const GameOverModal = ({ state, myColor, whiteName, blackName, onClose, onViewSaved }: GameOverModalProps) => {
  const winner = state.result === '1-0' ? 'white' : state.result === '0-1' ? 'black' : null;
  const isDraw = state.result === '1/2-1/2';
  const headline = (() => {
    if (isDraw) return 'Tablas';
    if (myColor && winner) return myColor === winner ? '¡Ganaste!' : 'Perdiste';
    if (winner === 'white') return `${whiteName} gana`;
    if (winner === 'black') return `${blackName} gana`;
    return 'Partida finalizada';
  })();
  const reasonLabel = (() => {
    switch (state.endReason) {
      case 'CHECKMATE': return 'Jaque mate';
      case 'RESIGN': return 'Por abandono';
      case 'STALEMATE': return 'Ahogado';
      case 'DRAW_INSUFFICIENT': return 'Material insuficiente';
      case 'DRAW_REPETITION': return 'Triple repetición';
      case 'DRAW_50MOVE': return 'Regla de 50 movimientos';
      default: return state.endReason ?? '';
    }
  })();

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        background: 'rgba(0,0,0,0.65)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        padding: 16, animation: 'cq-fade-in 200ms ease-out',
      }}
      onClick={onClose}
    >
      <style>{`
        @keyframes cq-fade-in { from { opacity: 0 } to { opacity: 1 } }
        @keyframes cq-pop-in { from { transform: scale(0.9); opacity: 0 } to { transform: scale(1); opacity: 1 } }
      `}</style>
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface, #1c1f1a)',
          border: '1px solid var(--border, #2a2d27)',
          borderRadius: 12,
          padding: '32px 28px',
          maxWidth: 420, width: '100%',
          textAlign: 'center',
          boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
          animation: 'cq-pop-in 250ms ease-out',
        }}
      >
        <div style={{ fontSize: 56, marginBottom: 8 }}>
          {isDraw ? '🤝' : myColor && winner === myColor ? '🏆' : myColor && winner ? '😔' : '♟'}
        </div>
        <h2 style={{ margin: '0 0 6px', fontSize: 28, fontWeight: 700 }}>{headline}</h2>
        <p style={{ margin: '0 0 4px', fontSize: 14, color: 'var(--text-muted)' }}>{reasonLabel}</p>
        <p style={{ margin: '0 0 24px', fontSize: 32, fontFamily: 'monospace', fontWeight: 700, letterSpacing: 2 }}>
          {state.result}
        </p>
        <div style={{ display: 'grid', gap: 8 }}>
          {onViewSaved && (
            <Button variant="secondary" onClick={onViewSaved}>
              Ver partida guardada #{state.finalizedGameId}
            </Button>
          )}
          <Button variant="primary" onClick={onClose}>
            Volver al portal
          </Button>
        </div>
      </div>
    </div>
  );
};

// ── Helpers ──────────────────────────────────────────────────────────────

function chessFromFen(fen: string): Chess | null {
  const setup = parseFen(fen);
  if (setup.isErr) return null;
  const pos = Chess.fromSetup(setup.value);
  return pos.isErr ? null : pos.value;
}

function inferPromotion(orig: Key, dest: Key, chess: Chess): string | null {
  // Si la pieza en orig es peón y dest está en la última fila → promoción default Q.
  // V1 no permite under-promotion; el usuario puede pedirlo después.
  const fromSq = parseSquare(orig);
  if (fromSq === undefined) return null;
  const piece = chess.board.get(fromSq);
  if (!piece || piece.role !== 'pawn') return null;
  const destRank = parseInt(dest[1], 10);
  if ((piece.color === 'white' && destRank === 8) || (piece.color === 'black' && destRank === 1)) {
    return 'q';
  }
  return null;
}

function groupMoves(moves: LiveMove[]): { white: string; black: string | null }[] {
  const out: { white: string; black: string | null }[] = [];
  for (const m of moves) {
    if (m.color === 'w') out.push({ white: `${m.moveNumber}. ${m.san}`, black: null });
    else if (out.length > 0) out[out.length - 1].black = m.san;
    else out.push({ white: '', black: m.san });
  }
  return out;
}

function message(e: unknown): string {
  if (typeof e === 'object' && e && 'response' in e) {
    const r = (e as { response?: { data?: { message?: string } } }).response;
    if (r?.data?.message) return r.data.message;
  }
  return e instanceof Error ? e.message : String(e);
}
