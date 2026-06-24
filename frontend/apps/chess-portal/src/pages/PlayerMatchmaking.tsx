import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
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

  // ELO ChessQuery actual del jugador: se envía como eloBefore para que el
  // cálculo parta de su rating real y NO del default 1500 en cada partida.
  const me = useQuery({
    queryKey: ['player', 'me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const startLive = useMutation({
    mutationFn: () => liveGameApi.create(me.data?.profile?.eloPlatform ?? undefined),
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
