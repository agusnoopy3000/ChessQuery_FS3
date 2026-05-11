import { useNavigate } from 'react-router-dom';
import { Button, Card } from '@chessquery/ui-lib';

/**
 * Landing pública — alcance demo: presentación + CTAs auth.
 */
export const HomePage = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: 28, display: 'grid', gap: 32 }}>
      <section className="landing-hero board-pattern" aria-labelledby="hero-title">
        <div className="hero-grid">
          <div>
            <div className="eyebrow">Plataforma chilena de ajedrez</div>
            <h1 id="hero-title" className="page-title">
              Compite, organiza y juega en tiempo real.
            </h1>
            <p className="page-copy">
              Jugadores, organizadores y federaciones en un solo lugar:
              partidas en vivo, gestión de torneos y rating sincronizado con FIDE.
            </p>
            <div className="hero-actions">
              <Button size="lg" variant="primary" onClick={() => navigate('/register')}>
                Crear cuenta
              </Button>
              <Button size="lg" onClick={() => navigate('/login')}>
                Iniciar sesión
              </Button>
            </div>
          </div>
        </div>
      </section>

      <section
        aria-label="Características principales"
        style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}
      >
        <Card>
          <div className="eyebrow">Para jugadores</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♞ Partidas en vivo
          </h3>
          <p className="page-copy">
            Emparejamiento 1 vs 1, validación de jugadas y PGN guardado.
            Tu rating se actualiza al terminar.
          </p>
        </Card>

        <Card>
          <div className="eyebrow">Para organizadores</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♜ Torneos suizos
          </h3>
          <p className="page-copy">
            Crea torneos, genera rondas y publica standings con Buchholz y Sonneborn-Berger.
          </p>
        </Card>

        <Card>
          <div className="eyebrow">Para federaciones</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♕ Rating oficial
          </h3>
          <p className="page-copy">
            Sincronización con FIDE, AJEFECH y Lichess. Perfiles enriquecidos automáticamente.
          </p>
        </Card>
      </section>

      <section style={{ textAlign: 'center', padding: '24px 0 8px' }}>
        <h2 className="page-title" style={{ fontSize: 24 }}>¿Listo para empezar?</h2>
        <p className="page-copy" style={{ maxWidth: 480, margin: '8px auto 20px' }}>
          Crea tu cuenta gratis y entra al portal del jugador.
        </p>
        <Button size="lg" variant="primary" onClick={() => navigate('/register')}>
          Registrarme
        </Button>
      </section>
    </div>
  );
};
