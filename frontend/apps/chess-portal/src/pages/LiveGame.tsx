import { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Chessground } from 'chessground';
import type { Api as CgApi } from 'chessground/api';
import type { Config as CgConfig } from 'chessground/config';
import type { Key } from 'chessground/types';
import { Chess } from 'chessops/chess';
import { parseFen } from 'chessops/fen';
import { parseSquare } from 'chessops/util';
import { chessgroundDests } from 'chessops/compat';
import { useAuth } from '@chessquery/shared';
import { Button, Card } from '@chessquery/ui-lib';
import { api } from '../api';
import { supabase } from '../lib/supabase';

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
  const { user } = useAuth();
  const cgRef = useRef<HTMLDivElement>(null);
  const cgApi = useRef<CgApi | null>(null);
  const [state, setState] = useState<LiveGameState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Determinar el color del usuario actual
  const myColor: 'white' | 'black' | null = useMemo(() => {
    if (!state || !user) return null;
    if (user.id === state.whitePlayerId) return 'white';
    if (user.id === state.blackPlayerId) return 'black';
    return null;
  }, [state, user]);

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

  // Subscripción a Supabase Realtime
  useEffect(() => {
    if (!id) return;
    const channel = supabase.channel(`game:${id}`, { config: { broadcast: { self: false } } });
    channel
      .on('broadcast', { event: 'move.played' }, () => { dataApi.get(id).then(setState).catch(() => {}); })
      .on('broadcast', { event: 'game.started' }, () => { dataApi.get(id).then(setState).catch(() => {}); })
      .on('broadcast', { event: 'game.finished' }, () => { dataApi.get(id).then(setState).catch(() => {}); })
      .subscribe();
    return () => { supabase.removeChannel(channel); };
  }, [id]);

  // Auto-join si soy el rival y la sesión está WAITING
  useEffect(() => {
    if (!id || !state || !user) return;
    if (state.status === 'WAITING' && user.id !== state.whitePlayerId && state.blackPlayerId == null) {
      dataApi.join(id).then(setState).catch((e) => setError(message(e)));
    }
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
    if (!id || !chess) return;
    // chessops UCI puede incluir promoción "q" si el peón llega a la última fila
    let uci = `${orig}${dest}`;
    const movePromotion = inferPromotion(orig, dest, chess);
    if (movePromotion) uci += movePromotion;

    setBusy(true);
    try {
      const next = await dataApi.move(id, uci);
      setState(next);
    } catch (e) {
      setError(message(e));
      // revertir tablero al estado anterior
      cgApi.current?.set({ fen: state?.currentFen });
    } finally {
      setBusy(false);
    }
  };

  const handleResign = async () => {
    if (!id || !confirm('¿Rendirse?')) return;
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

  return (
    <div className="page-shell" style={{ display: 'grid', gridTemplateColumns: 'minmax(320px, 560px) 320px', gap: 24 }}>
      <div>
        <div ref={cgRef} style={{ width: 560, height: 560, maxWidth: '100%', aspectRatio: '1 / 1' }} />
        {state.status === 'ACTIVE' && myColor && (
          <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
            <Button onClick={handleResign} disabled={busy} variant="danger">Rendirse</Button>
          </div>
        )}
      </div>

      <Card>
        <h2 style={{ marginTop: 0 }}>Partida {state.id}</h2>
        <p style={{ fontSize: 14, color: 'var(--text-muted)' }}>
          Estado: <strong>{state.status}</strong>
          {state.result && <> · Resultado: <strong>{state.result}</strong></>}
          {state.endReason && <> · {state.endReason}</>}
        </p>
        <p style={{ fontSize: 13 }}>
          ⚪ <strong>{state.whitePlayerId}</strong>{user?.id === state.whitePlayerId && ' (tú)'}<br />
          ⚫ <strong>{state.blackPlayerId ?? '— esperando rival —'}</strong>
          {user?.id === state.blackPlayerId && ' (tú)'}
        </p>

        {state.status === 'WAITING' && user?.id === state.whitePlayerId && (
          <Card style={{ marginTop: 12, background: 'var(--surface-2)' }}>
            <p style={{ margin: 0, fontSize: 13 }}>
              Comparte esta URL con tu rival:
            </p>
            <code style={{ display: 'block', wordBreak: 'break-all', marginTop: 6 }}>{shareUrl}</code>
          </Card>
        )}

        <h3 style={{ marginTop: 16 }}>Jugadas</h3>
        <ol style={{ fontFamily: 'monospace', fontSize: 13, maxHeight: 240, overflow: 'auto', paddingLeft: 22 }}>
          {groupMoves(state.moves).map((pair, i) => (
            <li key={i}>{pair.white} {pair.black ?? ''}</li>
          ))}
        </ol>

        {state.finalizedGameId && (
          <Link to={`/profile/${user?.id ?? ''}`} style={{ marginTop: 12, display: 'inline-block' }}>
            Partida guardada como #{state.finalizedGameId} →
          </Link>
        )}

        {error && <p style={{ color: 'crimson', marginTop: 12 }}>{error}</p>}
      </Card>
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
