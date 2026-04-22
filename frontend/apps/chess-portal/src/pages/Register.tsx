import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth, Role } from '@chessquery/shared';
import { Button, Input, Card, ErrorAlert, Select } from '@chessquery/ui-lib';

export const RegisterPage = () => {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirm: '',
    role: 'PLAYER' as Role,
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = <K extends keyof typeof form>(k: K, v: (typeof form)[K]) =>
    setForm((f) => ({ ...f, [k]: v }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (form.password.length < 8) {
      setError('La contraseña debe tener al menos 8 caracteres');
      return;
    }
    if (form.password !== form.confirm) {
      setError('Las contraseñas no coinciden');
      return;
    }
    setSubmitting(true);
    try {
      await register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        role: form.role,
      });
      navigate('/player/me');
    } catch (err) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(msg ?? 'No se pudo crear la cuenta');
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
      <div style={{ width: '100%', maxWidth: 480 }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ fontSize: 42, marginBottom: 4 }}>♔</div>
          <h1 style={{ fontSize: 26, letterSpacing: '-0.02em' }}>Crear cuenta</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Únete a ChessQuery</p>
        </div>

        <Card>
          <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {error && <ErrorAlert message={error} />}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <Input
                label="Nombre"
                value={form.firstName}
                required
                onChange={(e) => set('firstName', e.target.value)}
              />
              <Input
                label="Apellido"
                value={form.lastName}
                required
                onChange={(e) => set('lastName', e.target.value)}
              />
            </div>
            <Input
              label="Email"
              type="email"
              autoComplete="email"
              value={form.email}
              required
              onChange={(e) => set('email', e.target.value)}
            />
            <Input
              label="Contraseña"
              type="password"
              autoComplete="new-password"
              value={form.password}
              required
              minLength={8}
              hint="Mínimo 8 caracteres"
              onChange={(e) => set('password', e.target.value)}
            />
            <Input
              label="Confirmar contraseña"
              type="password"
              autoComplete="new-password"
              value={form.confirm}
              required
              onChange={(e) => set('confirm', e.target.value)}
            />
            <Select
              label="Rol"
              value={form.role}
              onChange={(e) => set('role', e.target.value as Role)}
              options={[
                { value: 'PLAYER', label: 'Jugador' },
                { value: 'ORGANIZER', label: 'Organizador' },
              ]}
            />
            <Button type="submit" loading={submitting} fullWidth size="lg">
              Crear cuenta →
            </Button>
          </form>
        </Card>

        <div style={{ textAlign: 'center', marginTop: 18, fontSize: 13, color: 'var(--text-muted)' }}>
          ¿Ya tienes cuenta?{' '}
          <Link to="/login" style={{ color: 'var(--accent)', fontWeight: 600 }}>
            Iniciar sesión
          </Link>
        </div>
      </div>
    </div>
  );
};
