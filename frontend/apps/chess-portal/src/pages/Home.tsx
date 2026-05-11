import { useNavigate } from 'react-router-dom';
import { Button, Card } from '@chessquery/ui-lib';

/**
 * Landing pública — alcance demo: presentación + CTAs auth.
 */
export const HomePage = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '16px 28px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 20, height: '100vh', overflow: 'hidden', boxSizing: 'border-box' }}>
      <section className="landing-hero board-pattern" aria-labelledby="hero-title" style={{ padding: '24px 32px' }}>
        <div className="hero-grid" style={{ gap: 16 }}>
          <div>
            <div className="eyebrow" style={{ marginBottom: 4 }}>Plataforma chilena de ajedrez</div>
            <h1 id="hero-title" className="page-title" style={{ fontSize: 32, marginBottom: 8, lineHeight: 1.15 }}>
              Compite, organiza y juega en tiempo real.
            </h1>
            <p className="page-copy" style={{ marginTop: 0, marginBottom: 16, fontSize: 13 }}>
              Jugadores, organizadores y federaciones en un solo lugar:
              partidas en vivo, gestión de torneos y rating sincronizado con FIDE.
            </p>
            <div className="hero-actions" style={{ gap: 12 }}>
              <Button size="md" variant="primary" onClick={() => navigate('/register')}>
                Crear cuenta
              </Button>
              <Button size="md" onClick={() => navigate('/login')}>
                Iniciar sesión
              </Button>
            </div>
          </div>
        </div>
      </section>

      <section
        aria-label="Características principales"
        style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}
      >
        <Card style={{ padding: '16px' }}>
          <div className="eyebrow">Para jugadores</div>
          <h3 className="page-title" style={{ fontSize: 18, marginTop: 4, marginBottom: 4 }}>
            ♞ Partidas en vivo
          </h3>
          <p className="page-copy" style={{ fontSize: 12, margin: 0 }}>
            Emparejamiento 1 vs 1, validación de jugadas y PGN guardado.
            Tu rating se actualiza al terminar.
          </p>
        </Card>

        <Card style={{ padding: '16px' }}>
          <div className="eyebrow">Para organizadores</div>
          <h3 className="page-title" style={{ fontSize: 18, marginTop: 4, marginBottom: 4 }}>
            ♜ Torneos suizos
          </h3>
          <p className="page-copy" style={{ fontSize: 12, margin: 0 }}>
            Crea torneos, genera rondas y publica standings con Buchholz y Sonneborn-Berger.
          </p>
        </Card>

        <Card style={{ padding: '16px' }}>
          <div className="eyebrow">Para federaciones</div>
          <h3 className="page-title" style={{ fontSize: 18, marginTop: 4, marginBottom: 4 }}>
            ♕ Rating oficial
          </h3>
          <p className="page-copy" style={{ fontSize: 12, margin: 0 }}>
            Sincronización con FIDE, AJEFECH y Lichess. Perfiles enriquecidos automáticamente.
          </p>
        </Card>
      </section>

      <section style={{ textAlign: 'center', padding: '12px 0' }}>
        <h2 className="page-title" style={{ fontSize: 20, margin: 0 }}>¿Listo para empezar?</h2>
        <p className="page-copy" style={{ maxWidth: 480, margin: '4px auto 12px', fontSize: 13 }}>
          Crea tu cuenta gratis y entra al portal del jugador.
        </p>
        <Button size="md" variant="primary" onClick={() => navigate('/register')}>
          Registrarme
        </Button>
      </section>
    </div>
  );
};
