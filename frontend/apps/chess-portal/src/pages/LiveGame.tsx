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
import type { Player } from '@chessquery/shared';
import { Button, Card } from '@chessquery/ui-lib';
import { api, playerApi } from '../api';
import { supabase } from '../lib/supabase';
import { useMyPlayerId } from '../hooks/useMyPlayerId';
import { computeMaterialBalance, flagFromIsoCode } from '../lib/chessHelpers';

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
  detectedOpeningEco: string | null;
  detectedOpeningName: string | null;
}

const dataApi = {
  get: (id: string) => api.get<LiveGameState>(`/api/player/play/live/${id}`).then((r) => r.data),
  join: (id: string, eloBefore?: number) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/join`, { eloBefore }).then((r) => r.data),
  move: (id: string, uci: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/move`, { uci }).then((r) => r.data),
  resign: (id: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/resign`).then((r) => r.data),
  draw: (id: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/draw`).then((r) => r.data),
  rematch: (id: string) =>
    api.post<LiveGameState>(`/api/player/play/live/${id}/rematch`).then((r) => r.data),
};

export const LiveGamePage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const cgRef = useRef<HTMLDivElement>(null);
  const cgApi = useRef<CgApi | null>(null);
  const channelRef = useRef<ReturnType<typeof supabase.channel> | null>(null);
  const [state, setState] = useState<LiveGameState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const myPlayerId = useMyPlayerId();
  const [whiteName, setWhiteName] = useState<string>('Blancas');
  const [blackName, setBlackName] = useState<string>('Negras');
  const [whiteProfile, setWhiteProfile] = useState<Player | null>(null);
  const [blackProfile, setBlackProfile] = useState<Player | null>(null);
  const [toasts, setToasts] = useState<{ id: number; text: string }[]>([]);
  const [promotionPick, setPromotionPick] = useState<{ orig: Key; dest: Key } | null>(null);
  const [viewIndex, setViewIndex] = useState<number | null>(null); // null = posición actual
  const [drawOfferState, setDrawOfferState] = useState<'idle' | 'sent' | 'received'>('idle');
  const [confirmingResign, setConfirmingResign] = useState(false);
  const [copied, setCopied] = useState(false);
  const lastMoveCountRef = useRef(0);
  const prevStatusRef = useRef<string | null>(null);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteSent, setInviteSent] = useState(false);
  const [inviteSending, setInviteSending] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [opponentOnline, setOpponentOnline] = useState(false);
  const [rematchSessionId, setRematchSessionId] = useState<number | null>(null);
  const [rematchCreating, setRematchCreating] = useState(false);

  // Resuelve perfiles de los jugadores (nombre + ELO + país) cuando cambian los IDs.
  useEffect(() => {
    if (!state) return;
    const fetchProfile = async (pid: number | null): Promise<{ name: string; profile: Player | null }> => {
      if (pid == null) return { name: '— esperando rival —', profile: null };
      try {
        const p = await playerApi.publicProfile(pid);
        const profile = p.profile ?? null;
        const n = `${profile?.firstName ?? ''} ${profile?.lastName ?? ''}`.trim();
        return { name: n || `Jugador ${pid}`, profile };
      } catch {
        return { name: `Jugador ${pid}`, profile: null };
      }
    };
    let cancelled = false;
    Promise.all([fetchProfile(state.whitePlayerId), fetchProfile(state.blackPlayerId)])
      .then(([w, b]) => {
        if (cancelled) return;
        setWhiteName(w.name);
        setBlackName(b.name);
        setWhiteProfile(w.profile);
        setBlackProfile(b.profile);
      });
    return () => { cancelled = true; };
  }, [state?.whitePlayerId, state?.blackPlayerId]);

  // Toast queue — autodismiss en 3s, una sola visible a la vez.
  const pushToast = (text: string) => {
    setToasts((prev) => [...prev, { id: Date.now() + Math.random(), text }]);
  };
  useEffect(() => {
    if (toasts.length === 0) return;
    const t = setTimeout(() => setToasts((prev) => prev.slice(1)), 3000);
    return () => clearTimeout(t);
  }, [toasts]);

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
      const wasOnline = opponentOnline;
      const nowOnline = String(opponentId) in presenceState;
      setOpponentOnline(nowOnline);
      if (!wasOnline && nowOnline) pushToast('Rival conectado');
      else if (wasOnline && !nowOnline) pushToast('Rival desconectado');
    };
    channel
      .on('broadcast', { event: 'move.played' }, ({ payload }) => applyOrFetch(payload))
      .on('broadcast', { event: 'game.started' }, ({ payload }) => applyOrFetch(payload))
      .on('broadcast', { event: 'game.finished' }, ({ payload }) => applyOrFetch(payload))
      .on('broadcast', { event: 'game.rematch.created' }, ({ payload }) => {
        const newId = (payload as { newSessionId?: number })?.newSessionId;
        if (typeof newId === 'number') setRematchSessionId(newId);
      })
      // R11: oferta de tablas — broadcast Realtime entre los dos jugadores.
      .on('broadcast', { event: 'draw.offered' }, () => {
        setDrawOfferState('received');
        pushToast('Tu rival ofrece tablas');
      })
      .on('broadcast', { event: 'draw.rejected' }, () => {
        setDrawOfferState('idle');
        pushToast('Tu rival rechazó las tablas');
      })
      .on('presence', { event: 'sync' }, refreshOpponent)
      .on('presence', { event: 'join' }, refreshOpponent)
      .on('presence', { event: 'leave' }, refreshOpponent)
      .subscribe(async (status) => {
        if (status === 'SUBSCRIBED' && myPlayerId != null) {
          channelRef.current = channel;
          await channel.track({ playerId: myPlayerId, online_at: new Date().toISOString() });
        }
      });
    return () => { channelRef.current = null; supabase.removeChannel(channel); };
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

  // FEN a renderizar: posición actual o histórica (R12).
  const viewedFen = useMemo(() => {
    if (!state) return null;
    if (viewIndex == null) return state.currentFen;
    if (viewIndex < 0 || viewIndex >= state.moves.length) return state.currentFen;
    return state.moves[viewIndex].fenAfter;
  }, [state, viewIndex]);
  const isReviewing = viewIndex != null && state != null && viewIndex < state.moves.length - 1;

  // Render del tablero
  useEffect(() => {
    if (!cgRef.current || !state || viewedFen == null) return;

    const chess = chessFromFen(viewedFen);
    const dests = chess ? chessgroundDests(chess) : new Map();
    const turnColor: 'white' | 'black' = state.turn === 'w' ? 'white' : 'black';
    const isMyTurn = myColor !== null && turnColor === myColor && state.status === 'ACTIVE' && !isReviewing;
    // Pre-move habilitado cuando es la partida en vivo y hay rival (R9).
    const allowPremove = myColor !== null && state.status === 'ACTIVE' && !isReviewing && !isMyTurn;

    const config: CgConfig = {
      fen: viewedFen,
      turnColor,
      orientation: myColor ?? 'white',
      check: chess?.isCheck() ? turnColor : undefined,
      viewOnly: isReviewing,
      movable: {
        free: false,
        color: isMyTurn ? myColor! : (allowPremove ? myColor! : undefined),
        dests: isMyTurn ? dests : new Map(),
        showDests: true,
        events: {
          after: (orig: Key, dest: Key) => {
            handleMove(orig, dest, chess);
          },
        },
      },
      premovable: {
        enabled: allowPremove,
        showDests: true,
        events: {
          set: (orig, dest) => {
            // R9: solo registramos; chessground reproduce el premove
            // automáticamente cuando el turno vuelve y dispara movable.events.after.
            void orig; void dest;
          },
          unset: () => { /* premove cancelado */ },
        },
      },
      draggable: { enabled: isMyTurn || allowPremove },
      animation: { enabled: true, duration: 200 },
      highlight: { lastMove: true, check: true },
    };

    if (cgApi.current) {
      cgApi.current.set(config);
    } else {
      cgApi.current = Chessground(cgRef.current, config);
    }
  }, [state, myColor, viewedFen, isReviewing]);

  // Cleanup chessground al desmontar
  useEffect(() => () => { cgApi.current?.destroy?.(); cgApi.current = null; }, []);

  const handleMove = async (orig: Key, dest: Key, chess: Chess | null) => {
    if (!id || !chess || !state) return;
    // R10: si la jugada es promoción de peón, abrir picker en vez de forzar Q.
    if (isPromotionMove(orig, dest, chess)) {
      setPromotionPick({ orig, dest });
      // Revertimos visualmente: el modal se encarga de mandar el move final.
      cgApi.current?.set({ fen: state.currentFen });
      return;
    }
    const uci = `${orig}${dest}`;

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

  // R11: oferta/aceptación de tablas vía Realtime broadcast.
  const offerDraw = () => {
    if (!channelRef.current) return;
    channelRef.current.send({ type: 'broadcast', event: 'draw.offered', payload: {} });
    setDrawOfferState('sent');
    pushToast('Oferta de tablas enviada');
  };
  const acceptDraw = async () => {
    if (!id) return;
    if (channelRef.current) {
      channelRef.current.send({ type: 'broadcast', event: 'game.finished', payload: {} });
    }
    setDrawOfferState('idle');
    setBusy(true);
    try {
      const next = await dataApi.draw(id);
      setState(next);
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  };
  const rejectDraw = () => {
    if (!channelRef.current) return;
    channelRef.current.send({ type: 'broadcast', event: 'draw.rejected', payload: {} });
    setDrawOfferState('idle');
  };

  // R10: el modal de promoción usa este helper para enviar la jugada con
  // la pieza elegida. Mantiene el optimismo visual usando el FEN actual.
  const submitPromotion = async (piece: 'q' | 'r' | 'b' | 'n') => {
    if (!id || !state || !promotionPick) return;
    const uci = `${promotionPick.orig}${promotionPick.dest}${piece}`;
    setPromotionPick(null);
    const fenBeforeMove = state.currentFen;
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
  const topProfile = myColor === 'black' ? whiteProfile : blackProfile;
  const bottomProfile = myColor === 'black' ? blackProfile : whiteProfile;
  const turnLabel = state.turn === 'w' ? whiteName : blackName;
  const turnColorView: 'white' | 'black' = state.turn === 'w' ? 'white' : 'black';
  const isMyTurn = myColor !== null && turnColorView === myColor && state.status === 'ACTIVE';

  const material = computeMaterialBalance(state.currentFen);
  // Si juego con negras, "top" del tablero es blancas → muestro sus capturas (piezas negras).
  const topCaptured = myColor === 'black' ? material.capturedByWhite : material.capturedByBlack;
  const bottomCaptured = myColor === 'black' ? material.capturedByBlack : material.capturedByWhite;
  const topDelta = myColor === 'black' ? material.delta : -material.delta;
  const bottomDelta = -topDelta;

  return (
    <div className="page-shell cq-live-grid">
      {/* Toast queue (R8): notificaciones de eventos Realtime */}
      {toasts.length > 0 && (
        <div
          style={{
            position: 'fixed', top: 16, left: '50%', transform: 'translateX(-50%)',
            background: 'rgba(20,22,18,0.95)', color: '#e8ead4',
            border: '1px solid var(--border, #2a2d27)', borderRadius: 8,
            padding: '10px 16px', fontSize: 13, fontWeight: 500,
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)', zIndex: 1100,
            animation: 'cq-toast-in 200ms ease-out',
          }}
        >
          <style>{`@keyframes cq-toast-in { from { opacity: 0; transform: translate(-50%, -8px) } to { opacity: 1; transform: translate(-50%, 0) } }`}</style>
          {toasts[0].text}
        </div>
      )}
      <style>{`
        .cq-live-grid {
          display: grid;
          grid-template-columns: minmax(0, 1fr) 340px;
          gap: 24px;
          padding: 24px;
          max-width: 1100px;
          margin: 0 auto;
          align-items: start;
        }
        .cq-live-board { width: min(560px, 100%); aspect-ratio: 1 / 1; }
        @media (max-width: 900px) {
          .cq-live-grid {
            grid-template-columns: minmax(0, 1fr);
            gap: 12px;
            padding: 12px;
          }
          .cq-live-board { width: min(560px, 100vw); max-width: calc(100vw - 24px); }
        }
        @media (max-width: 600px) {
          .cq-live-board { width: calc(100vw - 16px); max-width: 480px; }
        }
      `}</style>
      {/* Columna del tablero — centrado vertical y horizontal */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12 }}>
        <style>{`
          @keyframes cq-pulse-dot { 0%, 100% { transform: scale(1); opacity: 1 } 50% { transform: scale(1.4); opacity: 0.6 } }
          .cq-turn-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 8px; vertical-align: middle; }
          .cq-turn-banner { display: flex; align-items: center; padding: 8px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; transition: all 200ms ease; }
          .cq-turn-mine { background: rgba(120,200,80,0.18); color: #b9e090; border: 1px solid rgba(120,200,80,0.4); }
          .cq-turn-mine .cq-turn-dot { background: #7bd96a; animation: cq-pulse-dot 1.4s ease-in-out infinite; }
          .cq-turn-theirs { background: var(--surface-2, #15171a); color: var(--text-muted, #888); border: 1px solid var(--border, #2a2d27); }
          .cq-turn-theirs .cq-turn-dot { background: #777; }
        `}</style>

        <PlayerHeaderRow
          name={topName}
          profile={topProfile}
          colorIcon={myColor === 'black' ? '⚪' : '⚫'}
          showPresence={!!myColor && state.status === 'ACTIVE'}
          online={opponentOnline}
          captured={topCaptured}
          delta={topDelta}
        />

        {state.status === 'ACTIVE' && myColor && (
          <div className={`cq-turn-banner ${isMyTurn ? 'cq-turn-mine' : 'cq-turn-theirs'}`}>
            <span className="cq-turn-dot" />
            {isMyTurn ? 'Es tu turno' : `Esperando a ${opponentName}…`}
          </div>
        )}

        <div ref={cgRef} className="cq-live-board" />

        <PlayerHeaderRow
          name={`${bottomName}${myColor ? ' (tú)' : ''}`}
          profile={bottomProfile}
          colorIcon={myColor === 'black' ? '⚫' : '⚪'}
          showPresence={false}
          online={false}
          captured={bottomCaptured}
          delta={bottomDelta}
        />

        {state.status === 'ACTIVE' && myColor && (
          <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', justifyContent: 'center' }}>
            <Button
              onClick={handleResign}
              disabled={busy}
              variant="danger"
            >
              {confirmingResign ? '¿Seguro? Click para confirmar' : 'Rendirse'}
            </Button>
            <Button
              onClick={offerDraw}
              disabled={busy || drawOfferState === 'sent'}
              variant="secondary"
              title="Ofrecer tablas al rival"
            >
              {drawOfferState === 'sent' ? '🤝 Oferta enviada' : '🤝 Ofrecer tablas'}
            </Button>
          </div>
        )}

        {/* R12: navegación por historial */}
        {state.moves.length > 0 && (
          <div style={{ display: 'flex', gap: 6, marginTop: 8, alignItems: 'center', fontSize: 12, color: 'var(--text-muted)' }}>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setViewIndex((i) => {
                const cur = i ?? state.moves.length - 1;
                return Math.max(-1, cur - 1);
              })}
              disabled={viewIndex !== null && viewIndex <= -1}
              title="Jugada anterior"
            >←</Button>
            <span>
              {viewIndex == null
                ? `Jugada ${state.moves.length}/${state.moves.length}`
                : viewIndex < 0
                  ? `Inicial / ${state.moves.length}`
                  : `Jugada ${viewIndex + 1}/${state.moves.length}`}
            </span>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => setViewIndex((i) => {
                if (i == null) return null;
                const next = i + 1;
                if (next >= state.moves.length - 1) return null; // volver al "actual"
                return next;
              })}
              disabled={viewIndex == null}
              title="Jugada siguiente"
            >→</Button>
            {viewIndex != null && (
              <Button size="sm" variant="ghost" onClick={() => setViewIndex(null)}>
                Volver al actual
              </Button>
            )}
          </div>
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

        {state.detectedOpeningName && (
          <div
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '8px 10px', marginBottom: 12,
              background: 'rgba(120,180,255,0.08)',
              border: '1px solid rgba(120,180,255,0.25)',
              borderRadius: 6, fontSize: 13,
            }}
            title="Apertura detectada automáticamente del PGN"
          >
            <span style={{ fontSize: 16 }}>📖</span>
            <span>
              <strong>{state.detectedOpeningName}</strong>
              {state.detectedOpeningEco && (
                <span style={{ marginLeft: 6, color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 11 }}>
                  {state.detectedOpeningEco}
                </span>
              )}
            </span>
          </div>
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

      {/* R10: modal under-promotion picker */}
      {promotionPick && myColor && (
        <PromotionPicker
          color={myColor}
          onPick={submitPromotion}
          onCancel={() => {
            setPromotionPick(null);
            // Restauramos el board al FEN actual para que el usuario pueda reintentar.
            cgApi.current?.set({ fen: state.currentFen });
          }}
        />
      )}

      {/* R11: modal oferta de tablas recibida */}
      {drawOfferState === 'received' && state.status === 'ACTIVE' && (
        <div
          style={{
            position: 'fixed', inset: 0, zIndex: 1050, background: 'rgba(0,0,0,0.55)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
          }}
        >
          <div
            style={{
              background: 'var(--surface, #1c1f1a)', border: '1px solid var(--border, #2a2d27)',
              borderRadius: 12, padding: '24px 24px', maxWidth: 360, width: '100%', textAlign: 'center',
              boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
            }}
          >
            <div style={{ fontSize: 40, marginBottom: 8 }}>🤝</div>
            <h2 style={{ margin: '0 0 8px', fontSize: 20 }}>Tu rival ofrece tablas</h2>
            <p style={{ margin: '0 0 16px', fontSize: 13, color: 'var(--text-muted)' }}>
              Si aceptas, la partida termina en 1/2-1/2.
            </p>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
              <Button variant="primary" onClick={acceptDraw} disabled={busy}>Aceptar tablas</Button>
              <Button variant="ghost" onClick={rejectDraw} disabled={busy}>Rechazar</Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal fin de partida — overlay con resultado y CTAs */}
      {state.status === 'FINISHED' && (
        <GameOverModal
          state={state}
          myColor={myColor}
          whiteName={whiteName}
          blackName={blackName}
          onClose={() => navigate('/play')}
          onViewSaved={state.finalizedGameId ? () => navigate(`/player/${myPlayerId ?? ''}`) : null}
          onRematch={async () => {
            if (!id) return;
            // Si el rival ya creó la revancha, navegamos directo. Si no, la creamos.
            if (rematchSessionId != null) {
              navigate(`/play/${rematchSessionId}`);
              return;
            }
            setRematchCreating(true);
            try {
              const next = await dataApi.rematch(id);
              navigate(`/play/${next.id}`);
            } catch (e) {
              setError(message(e));
            } finally {
              setRematchCreating(false);
            }
          }}
          rematchPending={rematchSessionId != null}
          rematchCreating={rematchCreating}
        />
      )}
    </div>
  );
};

interface PromotionPickerProps {
  color: 'white' | 'black';
  onPick: (piece: 'q' | 'r' | 'b' | 'n') => void;
  onCancel: () => void;
}

const PromotionPicker = ({ color, onPick, onCancel }: PromotionPickerProps) => {
  const symbols: Record<'q' | 'r' | 'b' | 'n', string> = color === 'white'
    ? { q: '♕', r: '♖', b: '♗', n: '♘' }
    : { q: '♛', r: '♜', b: '♝', n: '♞' };
  const labels: Record<'q' | 'r' | 'b' | 'n', string> = { q: 'Reina', r: 'Torre', b: 'Alfil', n: 'Caballo' };
  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1100, background: 'rgba(0,0,0,0.55)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
      }}
      onClick={onCancel}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface, #1c1f1a)', border: '1px solid var(--border, #2a2d27)',
          borderRadius: 12, padding: 20, maxWidth: 320, width: '100%', textAlign: 'center',
          boxShadow: '0 20px 60px rgba(0,0,0,0.5)',
        }}
      >
        <h3 style={{ margin: '0 0 12px', fontSize: 16 }}>Elegí pieza para promocionar</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
          {(['q', 'r', 'b', 'n'] as const).map((p) => (
            <button
              key={p}
              onClick={() => onPick(p)}
              autoFocus={p === 'q'}
              style={{
                background: p === 'q' ? 'rgba(106,191,116,0.18)' : 'var(--surface-2, #15171a)',
                border: `1px solid ${p === 'q' ? 'rgba(106,191,116,0.5)' : 'var(--border, #2a2d27)'}`,
                borderRadius: 10, padding: '14px 0', cursor: 'pointer',
                fontSize: 32, lineHeight: 1, color: 'var(--text, #e8ead4)',
              }}
              title={labels[p]}
            >
              {symbols[p]}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};

interface PlayerHeaderRowProps {
  name: string;
  profile: Player | null;
  colorIcon: string;
  showPresence: boolean;
  online: boolean;
  captured: { piece: string; symbol: string }[];
  delta: number;
}

const PlayerHeaderRow = ({
  name, profile, colorIcon, showPresence, online, captured, delta,
}: PlayerHeaderRowProps) => {
  const elo = profile?.eloNational ?? null;
  const flag = flagFromIsoCode(profile?.countryIsoCode);
  const eloLabel = elo != null ? String(elo) : 'Sin rating';
  return (
    <div style={{ alignSelf: 'flex-start', display: 'flex', flexDirection: 'column', gap: 2 }}>
      <div style={{ fontSize: 14, fontWeight: 600, fontFamily: '"Noto Sans", "Helvetica Neue", sans-serif' }}>
        {colorIcon} {name}
        <span style={{ marginLeft: 6, color: 'var(--text-muted)', fontWeight: 500 }}>
          · {eloLabel}
          {flag && <> · <span style={{ fontSize: 14 }}>{flag}</span></>}
        </span>
        {showPresence && (
          <span title={online ? 'Conectado' : 'Desconectado'} style={{ marginLeft: 8, fontSize: 10 }}>
            {online ? '🟢' : '🔴'}
          </span>
        )}
      </div>
      {(captured.length > 0 || delta > 0) && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 16, lineHeight: 1, minHeight: 16 }}>
          {captured.map((c, i) => (
            <span key={i} style={{ color: 'var(--text-muted)' }}>{c.symbol}</span>
          ))}
          {delta > 0 && (
            <span style={{ marginLeft: 4, fontSize: 12, fontWeight: 600, color: 'var(--text-muted)' }}>
              +{delta}
            </span>
          )}
        </div>
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
  onRematch: () => void;
  rematchPending: boolean;
  rematchCreating: boolean;
}

const GameOverModal = ({
  state, myColor, whiteName, blackName, onClose, onViewSaved,
  onRematch, rematchPending, rematchCreating,
}: GameOverModalProps) => {
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
          <Button variant="primary" onClick={onRematch} loading={rematchCreating}>
            {rematchPending ? '🔁 Aceptar revancha del rival' : '🔁 Revancha (colores invertidos)'}
          </Button>
          {onViewSaved && (
            <Button variant="secondary" onClick={onViewSaved}>
              Ver partida guardada #{state.finalizedGameId}
            </Button>
          )}
          <Button variant="ghost" onClick={onClose}>
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

function isPromotionMove(orig: Key, dest: Key, chess: Chess): boolean {
  const fromSq = parseSquare(orig);
  if (fromSq === undefined) return false;
  const piece = chess.board.get(fromSq);
  if (!piece || piece.role !== 'pawn') return false;
  const destRank = parseInt(dest[1], 10);
  return (piece.color === 'white' && destRank === 8) || (piece.color === 'black' && destRank === 1);
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
