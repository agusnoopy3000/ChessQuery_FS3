import { useNavigate } from 'react-router-dom';
import { Button, Card } from '@chessquery/ui-lib';

/**
 * Landing pública mínima — alcance demo:
 * solo presentación institucional + CTAs de login/registro.
 *
 * Las vistas públicas de ranking, torneos y búsqueda fueron removidas
 * del alcance de la demo. Los endpoints backend siguen operativos.
 */
export const HomePage = () => {
  const navigate = useNavigate();

  return (
    <div style={{ padding: 28, display: 'grid', gap: 24 }}>
      <section className="landing-hero board-pattern">
        <div className="hero-grid">
          <div>
            <div className="eyebrow">ChessQuery · Plataforma chilena de ajedrez</div>
            <h1 className="page-title">Competí, organizá y jugá en tiempo real.</h1>
            <p className="page-copy">
              Una plataforma de ajedrez para Chile que une a jugadores, organizadores
              y federaciones bajo un mismo ecosistema: matchmaking en vivo, gestión
              de torneos, y rating sincronizado con FIDE / AJEFECH / Lichess.
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

      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
        <Card>
          <div className="eyebrow">Para jugadores</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♞ Partidas en vivo
          </h3>
          <p className="page-copy">
            Emparejamientos 1 vs 1 con tablero interactivo, validación de jugadas
            en tiempo real y registro automático de PGN. Tu rating se actualiza al
            terminar cada partida.
          </p>
        </Card>

        <Card>
          <div className="eyebrow">Para organizadores</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♜ Torneos suizos / round-robin
          </h3>
          <p className="page-copy">
            Crea torneos, gestiona inscripciones, genera rondas con emparejamiento
            automático y publica standings con desempates Buchholz / Sonneborn-Berger.
          </p>
        </Card>

        <Card>
          <div className="eyebrow">Para federaciones</div>
          <h3 className="page-title" style={{ fontSize: 20, marginTop: 8 }}>
            ♕ Rating sincronizado
          </h3>
          <p className="page-copy">
            Sincronización con FIDE, AJEFECH y Lichess vía ETL. Los perfiles de
            jugadores se enriquecen automáticamente con rating oficial y actividad
            online.
          </p>
        </Card>
      </section>

      <section style={{ textAlign: 'center', padding: '32px 0' }}>
        <h2 className="page-title" style={{ fontSize: 24 }}>¿Listo para empezar?</h2>
        <p className="page-copy" style={{ maxWidth: 520, margin: '8px auto 16px' }}>
          Creá una cuenta gratis para entrar al portal del jugador y empezar a
          jugar contra rivales en vivo.
        </p>
        <Button size="lg" variant="primary" onClick={() => navigate('/register')}>
          Registrarme
        </Button>
      </section>
    </div>
  );
};
