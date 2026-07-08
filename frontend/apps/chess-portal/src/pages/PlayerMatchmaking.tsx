import { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  DEFAULT_PRESET_ID,
  MODALITY_LABELS,
  MODALITY_ORDER,
  presetById,
  presetsByFamily,
} from '@chessquery/shared';
import { Button, Card, ErrorAlert } from '@chessquery/ui-lib';
import { liveGameApi, playerApi } from '../api';
import { TransitionOverlay } from '../components/TransitionOverlay';

/**
 * Vista 2: Jugar (Emparejamiento) — alcance demo.
 *
 * Flujo único: el jugador crea una sesión live, recibe la URL, la comparte
 * con su rival. Cuando el rival entra, ambos juegan en tiempo real con
 * tablero validado y la partida se persiste con su PGN al terminar.
 *
 * El flujo legacy de "buscar rival sugerido + reportar resultado manual"
 * fue removido: hoy todo pasa por sesiones live.
 */
export const PlayerMatchmakingPage = () => {
  const navigate = useNavigate();
  const [redirecting, setRedirecting] = useState(false);
  const [presetId, setPresetId] = useState(DEFAULT_PRESET_ID);

  const selectedPreset = useMemo(
    () => presetById(presetId) ?? presetById(DEFAULT_PRESET_ID)!,
    [presetId],
  );

  // ELO ChessQuery actual del jugador: se envía como eloBefore para que el
  // cálculo parta de su rating real y NO del default 1500 en cada partida.
  const me = useQuery({
    queryKey: ['player', 'me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const startLive = useMutation({
    mutationFn: () =>
      liveGameApi.create(me.data?.profile?.eloPlatform ?? undefined, {
        initialMs: selectedPreset.initialMs,
        incrementMs: selectedPreset.incrementMs,
      }),
    onSuccess: (game) => {
      setRedirecting(true);
      navigate(`/play/${game.id}`);
    },
    onError: () => setRedirecting(false),
  });

  const errorMessage =
    (startLive.error as { response?: { data?: { message?: string } }; message?: string })
      ?.response?.data?.message ??
    (startLive.error as { message?: string })?.message ??
    'Error desconocido';

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20, maxWidth: 720, margin: '0 auto' }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Portal de Juego</div>
          <h1 className="page-title">Empezar partida</h1>
          <p className="page-copy">
            Crea una partida y comparte el enlace con tu rival. En cuanto entre, juegan
            1 vs 1 en tiempo real con su reloj. Al terminar la partida queda guardada y
            tu rating se actualiza automáticamente.
          </p>
        </div>
      </section>

      <Card>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 18,
            padding: '40px 16px',
          }}
        >
          <div style={{ fontSize: 64 }}>♞</div>

          <div style={{ textAlign: 'center', color: 'var(--text-muted)', maxWidth: 480 }}>
            Vas a jugar de blancas. Tu rival entra cuando abra el enlace que vas a
            recibir.
          </div>

          <div style={{ width: '100%', maxWidth: 480, display: 'grid', gap: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-muted)' }}>
              Modalidad y reloj
            </div>
            {MODALITY_ORDER.map((family) => (
              <div key={family} style={{ display: 'grid', gap: 6 }}>
                <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
                  {MODALITY_LABELS[family]}
                </div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  {presetsByFamily(family).map((p) => {
                    const active = p.id === selectedPreset.id;
                    return (
                      <button
                        key={p.id}
                        type="button"
                        aria-pressed={active}
                        onClick={() => setPresetId(p.id)}
                        style={{
                          padding: '6px 14px',
                          borderRadius: 8,
                          cursor: 'pointer',
                          fontWeight: 600,
                          border: active
                            ? '2px solid var(--accent, #4f7cff)'
                            : '1px solid var(--border, #d0d0d0)',
                          background: active ? 'var(--accent-soft, #eef2ff)' : 'transparent',
                          color: 'inherit',
                        }}
                      >
                        {p.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>

          <Button
            size="lg"
            variant="primary"
            onClick={() => startLive.mutate()}
            loading={startLive.isPending || redirecting}
          >
            {redirecting ? '♞ Abriendo tablero…' : '♞ Empezar partida en vivo'}
          </Button>

          {startLive.isError && (
            <ErrorAlert title="No se pudo crear la partida" message={errorMessage} />
          )}
        </div>
      </Card>
      {redirecting && (
        <TransitionOverlay
          message="Preparando tablero"
          detail="Creando la sala en vivo y conectando Realtime."
        />
      )}
    </div>
  );
};
