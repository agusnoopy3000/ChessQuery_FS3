import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Button, Card, ErrorAlert } from '@chessquery/ui-lib';
import { liveGameApi } from '../api';

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

  const startLive = useMutation({
    mutationFn: () => liveGameApi.create(),
    onSuccess: (game) => navigate(`/play/${game.id}`),
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
            Creá una sesión nueva y compartí la URL con tu rival. Al entrar,
            jugarán 1 vs 1 en tiempo real con cada jugada validada en el servidor.
            La partida se guarda automáticamente al terminar (mate, rendición, tablas).
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
            loading={startLive.isPending}
          >
            ♞ Empezar partida en vivo
          </Button>

          {startLive.isError && (
            <ErrorAlert title="No se pudo crear la partida" message={errorMessage} />
          )}
        </div>
      </Card>
    </div>
  );
};
