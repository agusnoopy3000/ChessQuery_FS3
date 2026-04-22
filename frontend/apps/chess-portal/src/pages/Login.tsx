import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Button, Input, Card, ErrorAlert } from '@chessquery/ui-lib';

export const LoginPage = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      navigate('/player/me');
    } catch (err) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'Credenciales inválidas');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: 'var(--bg)',
      }}
    >
      <div style={{ width: '100%', maxWidth: 420 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 42, marginBottom: 4 }}>♔</div>
          <h1 style={{ fontSize: 26, letterSpacing: '-0.02em' }}>
            Chess<span style={{ color: 'var(--accent)' }}>Query</span>
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Inicia sesión</p>
        </div>

        <Card>
          <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {error && <ErrorAlert message={error} />}
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              value={email}
              required
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tu@correo.cl"
            />
            <Input
              label="Contraseña"
              type="password"
              autoComplete="current-password"
              value={password}
              required
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
            <Button type="submit" loading={submitting} fullWidth size="lg">
              Entrar →
            </Button>
          </form>
        </Card>

        <div style={{ textAlign: 'center', marginTop: 18, fontSize: 13, color: 'var(--text-muted)' }}>
          ¿No tienes cuenta?{' '}
          <Link to="/register" style={{ color: 'var(--accent)', fontWeight: 600 }}>
            Crear una
          </Link>
        </div>
        <div style={{ textAlign: 'center', marginTop: 8, fontSize: 12 }}>
          <Link to="/" style={{ color: 'var(--text-dim)' }}>
            ← Volver al inicio
          </Link>
        </div>
      </div>
    </div>
  );
};
