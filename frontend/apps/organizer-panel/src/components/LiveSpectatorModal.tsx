import { useEffect, useState } from 'react';
import { Modal, ChessBoard, Badge, Skeleton } from '@chessquery/ui-lib';
import { supabase } from '../lib/supabase';
import { liveGameApi, LiveGameSummary } from '../api';

interface Props {
  sessionId: number;
  /** Etiquetas opcionales de los jugadores (del pairing). */
  whiteLabel?: string;
  blackLabel?: string;
  onClose: () => void;
}

const statusLabel = (s: string, result: string | null): string => {
  if (s === 'FINISHED') return result ? `Finalizada · ${result}` : 'Finalizada';
  if (s === 'ACTIVE') return 'En juego';
  if (s === 'WAITING') return 'Esperando jugadores';
  return s;
};

/**
 * Tablero espectador en tiempo real para el organizador. Toma el snapshot
 * inicial vía REST y luego escucha el canal Supabase Realtime `game:{id}`
 * (mismos eventos que emite ms-game: game.started / move.played / game.finished),
 * cada uno con el estado completo en `payload.state`. Solo lectura.
 */
export const LiveSpectatorModal = ({ sessionId, whiteLabel, blackLabel, onClose }: Props) => {
  const [state, setState] = useState<LiveGameSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    liveGameApi
      .get(sessionId)
      .then((s) => { if (active) setState(s); })
      .catch(() => { if (active) setError('No se pudo cargar la partida'); });

    const applyFromPayload = (payload: unknown) => {
      const p = payload as { state?: LiveGameSummary };
      if (p?.state) setState(p.state);
    };

    const channel = supabase
      .channel(`game:${sessionId}`, { config: { broadcast: { self: false } } })
      .on('broadcast', { event: 'game.started' }, ({ payload }) => applyFromPayload(payload))
      .on('broadcast', { event: 'move.played' }, ({ payload }) => applyFromPayload(payload))
      .on('broadcast', { event: 'game.finished' }, ({ payload }) => applyFromPayload(payload))
      .subscribe();

    return () => {
      active = false;
      supabase.removeChannel(channel);
    };
  }, [sessionId]);

  const turnLabel = state?.turn === 'b' ? 'Negras' : 'Blancas';

  return (
    <Modal open onClose={onClose} title={`Partida en vivo · mesa #${sessionId}`} size="md">
      {error ? (
        <p style={{ color: 'var(--cq-error, #e05a5a)' }}>{error}</p>
      ) : !state ? (
        <Skeleton height={320} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center' }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'center' }}>
            <Badge variant={state.status === 'ACTIVE' ? 'success' : state.status === 'FINISHED' ? 'neutral' : 'warning'}>
              {statusLabel(state.status, state.result ?? null)}
            </Badge>
            {state.status === 'ACTIVE' && <Badge variant="info">Turno: {turnLabel}</Badge>}
          </div>

          <div style={{ fontSize: 13 }}>
            ⚪ <strong>{whiteLabel ?? `#${state.whitePlayerId ?? '—'}`}</strong>
            {'   vs   '}
            ⚫ <strong>{blackLabel ?? `#${state.blackPlayerId ?? '—'}`}</strong>
          </div>

          <ChessBoard fen={state.currentFen} size={340} />

          <p style={{ fontSize: 11, color: 'var(--cq-text-muted, #7a7d6e)', margin: 0 }}>
            Actualización en vivo · solo lectura
          </p>
        </div>
      )}
    </Modal>
  );
};
