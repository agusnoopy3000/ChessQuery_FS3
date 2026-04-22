import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Role, useAuth } from '@chessquery/shared';
import { Badge, Button, Card, ErrorAlert, Input } from '@chessquery/ui-lib';
import { getDefaultRoute } from '../portal-utils';

const roleOptions: Array<{
  value: Role;
  title: string;
  icon: string;
  description: string;
  detail: string;
}> = [
  {
    value: 'PLAYER',
    title: 'Jugador',
    icon: '♞',
    description: 'Compite, consulta rankings y juega desde tu portal.',
    detail: 'Visual pensado como ajedrecista frente al tablero y foco en matchups.',
  },
  {
    value: 'ORGANIZER',
    title: 'Organizador',
    icon: '♖',
    description: 'Gestiona clubes, academias, torneos y validación de perfiles.',
    detail: 'Visual de mesa arbitral con tablero, control de rondas y seguimiento.',
  },
];

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

  const activeRole = useMemo(
    () => roleOptions.find((option) => option.value === form.role) ?? roleOptions[0],
    [form.role],
  );

  const set = <K extends keyof typeof form>(key: K, value: (typeof form)[K]) =>
    setForm((current) => ({ ...current, [key]: value }));

  const onSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
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
      const user = await register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        role: form.role,
      });
      navigate(getDefaultRoute(user.role));
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(message ?? 'No se pudo crear la cuenta');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-shell">
      <div className="auth-grid">
        <section className="auth-visual role-showcase">
          <div className="eyebrow">Registro por rol</div>
          <h1 className="page-title">Elige cómo entrar al ecosistema ChessQuery.</h1>
          <p className="page-copy">
            Construí esta pantalla para que el rol se entienda visualmente antes del submit: un jugador cerca del
            tablero y un organizador con una mesa de control competitiva.
          </p>

          <div className="role-grid">
            {roleOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                className="role-option"
                data-active={form.role === option.value}
                onClick={() => set('role', option.value)}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'start' }}>
                  <div>
                    <div className="role-icon">{option.icon}</div>
                    <div style={{ fontSize: 22, fontWeight: 700, marginTop: 10 }}>{option.title}</div>
                    <div style={{ color: 'var(--text-muted)', marginTop: 8 }}>{option.description}</div>
                  </div>
                  <Badge variant={form.role === option.value ? 'success' : 'neutral'}>
                    {form.role === option.value ? 'Seleccionado' : 'Disponible'}
                  </Badge>
                </div>
                <div className="board-mini">
                  <div />
                  <div />
                  <div />
                  <div />
                </div>
                <div style={{ color: 'var(--text-dim)', fontSize: 12 }}>{option.detail}</div>
              </button>
            ))}
          </div>
        </section>

        <section className="auth-form-shell">
          <div style={{ width: '100%', maxWidth: 480 }}>
            <div style={{ marginBottom: 20 }}>
              <div className="eyebrow">Nuevo acceso</div>
              <h2 className="page-title" style={{ fontSize: 34 }}>
                Crear cuenta
              </h2>
              <p className="page-copy">{activeRole.title} · {activeRole.description}</p>
            </div>

            <Card className="surface-panel">
              <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
                {error ? <ErrorAlert message={error} /> : null}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                  <Input label="Nombre" value={form.firstName} required onChange={(event) => set('firstName', event.target.value)} />
                  <Input label="Apellido" value={form.lastName} required onChange={(event) => set('lastName', event.target.value)} />
                </div>
                <Input
                  label="Email"
                  type="email"
                  autoComplete="email"
                  value={form.email}
                  required
                  onChange={(event) => set('email', event.target.value)}
                />
                <Input
                  label="Contraseña"
                  type="password"
                  autoComplete="new-password"
                  value={form.password}
                  required
                  minLength={8}
                  hint="Mínimo 8 caracteres"
                  onChange={(event) => set('password', event.target.value)}
                />
                <Input
                  label="Confirmar contraseña"
                  type="password"
                  autoComplete="new-password"
                  value={form.confirm}
                  required
                  onChange={(event) => set('confirm', event.target.value)}
                />
                <Button type="submit" size="lg" loading={submitting} fullWidth>
                  Crear cuenta {activeRole.title.toLowerCase()}
                </Button>
              </form>
            </Card>

            <div className="auth-links">
              <span>¿Ya tienes cuenta?</span>
              <Link to="/login">Iniciar sesión</Link>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};
