import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Button, Card, ErrorAlert, Input } from '@chessquery/ui-lib';
import { resolveRequestedRoute } from '../portal-utils';

export const LoginPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      const user = await login(email.trim(), password);
      navigate(resolveRequestedRoute(user.role, params.get('next')));
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(message ?? 'Credenciales inválidas');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-shell">
      <div className="auth-grid">
        <section className="auth-visual board-pattern">
          <div className="eyebrow">ChessQuery</div>
          <h1 className="page-title">Accede a tu tablero competitivo.</h1>
          <p className="page-copy">
            Diseñé esta entrada con una paleta cercana a Lichess: grafito, madera clara y un acento verde de juego
            para que el portal se sienta más ajedrecístico desde el primer pantallazo.
          </p>

          <div className="auth-stat-row">
            <div className="auth-stat-card">
              <div className="metric-label">Jugador</div>
              <div className="metric-value">Portal</div>
              <div className="page-copy">Matchmaking, rankings y perfiles.</div>
            </div>
            <div className="auth-stat-card">
              <div className="metric-label">Organizador</div>
              <div className="metric-value">Mesa</div>
              <div className="page-copy">Validación, rondas y torneos.</div>
            </div>
          </div>
        </section>

        <section className="auth-form-shell">
          <div style={{ width: '100%', maxWidth: 440 }}>
            <div style={{ marginBottom: 20 }}>
              <div className="eyebrow">Login</div>
              <h2 className="page-title" style={{ fontSize: 34 }}>
                Inicia sesión
              </h2>
              <p className="page-copy">Usa tu cuenta para entrar al flujo correspondiente según tu rol.</p>
            </div>

            <Card className="surface-panel">
              <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
                {error ? <ErrorAlert message={error} /> : null}
                <Input
                  label="Email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  required
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="tu@club.cl"
                />
                <Input
                  label="Contraseña"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  required
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="••••••••"
                />
                <Button type="submit" size="lg" loading={submitting} fullWidth>
                  Entrar al portal
                </Button>
              </form>
            </Card>

            <div className="auth-links">
              <span>¿Todavía no tienes cuenta?</span>
              <Link to="/register">Crear una</Link>
            </div>
            <div className="auth-links subtle">
              <Link to="/">← Volver al inicio</Link>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
