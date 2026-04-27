import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Button, Input, Card, ErrorAlert } from '@chessquery/ui-lib';

export const OrganizerLoginPage = () => {
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
      const u = await login(email.trim(), password);
      if (u.role !== 'ORGANIZER' && u.role !== 'ADMIN') {
        setError('Esta cuenta no tiene rol de organizador');
        return;
      }
      navigate('/');
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
          <div style={{ fontSize: 42, marginBottom: 4 }}>♞</div>
          <h1 style={{ fontSize: 24 }}>Panel Organizador</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Ingresa con tu cuenta</p>
        </div>
        <Card>
          <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {error && <ErrorAlert message={error} />}
            <Input label="Email" type="email" value={email} required onChange={(e) => setEmail(e.target.value)} />
            <Input label="Contraseña" type="password" value={password} required onChange={(e) => setPassword(e.target.value)} />
            <Button type="submit" loading={submitting} fullWidth size="lg">
              Entrar →
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
};
