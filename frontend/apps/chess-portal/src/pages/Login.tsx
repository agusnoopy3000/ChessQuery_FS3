import { ChangeEvent, FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth, translateAuthError } from '@chessquery/shared';
import { playerApi } from '../api';

/* ── Logo ── */
const ChessQueryLogo = () => (
  <svg viewBox="0 0 28 28" fill="none" style={{ width: '100%', height: '100%' }}>
    <rect width="28" height="28" rx="6" fill="currentColor" opacity=".15" />
    <path d="M8 22v-2.5H6V16h2v-2h2v-2h4v2h2v2h2v3.5h-2V22H8z" fill="currentColor" />
    <rect x="11" y="6" width="2.5" height="5" rx="1.25" fill="currentColor" />
    <rect x="8" y="8" width="8" height="2.5" rx="1.25" fill="currentColor" />
  </svg>
);

/* ── Caballo (knight) animado ── */
const KnightSVG = () => (
  <svg viewBox="0 0 40 56" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ width: '100%', height: '100%' }}>
    <ellipse cx="20" cy="51" rx="14" ry="4" fill="currentColor" opacity=".22" />
    <rect x="9" y="42" width="22" height="8" rx="3" fill="currentColor" />
    <path d="M11 42 Q9 30 12 22 Q14 16 20 14 L22 8 Q24 4 28 6 Q32 8 31 14 Q34 18 33 24 Q34 30 32 36 L30 42Z" fill="currentColor" />
    <circle cx="26" cy="14" r="1.4" fill="#0e100d" />
    <path d="M14 22 Q18 20 22 22" stroke="#0e100d" strokeWidth="1" strokeLinecap="round" opacity=".5" />
  </svg>
);

interface Move {
  from: [number, number];
  to: [number, number];
}

const CHESS_MOVES: Move[] = [
  { from: [6, 4], to: [4, 4] },
  { from: [1, 4], to: [3, 4] },
  { from: [7, 6], to: [5, 5] },
  { from: [0, 1], to: [2, 2] },
  { from: [7, 5], to: [4, 2] },
  { from: [5, 5], to: [3, 4] },
];

/* ── Tablero animado del panel izquierdo ── */
const ChessBackdrop = () => {
  const [highlight, setHighlight] = useState<Move | null>(null);

  useEffect(() => {
    let i = 0;
    const tick = () => {
      setHighlight(CHESS_MOVES[i % CHESS_MOVES.length]);
      i += 1;
    };
    tick();
    const id = setInterval(tick, 1800);
    return () => clearInterval(id);
  }, []);

  const cellSize = 30;
  return (
    <div style={{ position: 'relative', width: cellSize * 8, height: cellSize * 8, margin: '0 auto' }}>
      <div
        style={{
          position: 'absolute',
          inset: -20,
          borderRadius: 20,
          background: 'radial-gradient(circle, rgba(106,191,116,0.08), transparent 70%)',
          pointerEvents: 'none',
        }}
      />
      <div style={{ position: 'absolute', left: -18, top: 0, height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'space-around' }}>
        {['8', '7', '6', '5', '4', '3', '2', '1'].map((n) => (
          <span key={n} style={{ fontFamily: 'Space Mono, monospace', fontSize: 9, color: '#4a4d40' }}>
            {n}
          </span>
        ))}
      </div>
      <div style={{ position: 'absolute', bottom: -18, left: 0, width: '100%', display: 'flex', justifyContent: 'space-around' }}>
        {['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'].map((n) => (
          <span key={n} style={{ fontFamily: 'Space Mono, monospace', fontSize: 9, color: '#4a4d40' }}>
            {n}
          </span>
        ))}
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(8, ${cellSize}px)`,
          gridTemplateRows: `repeat(8, ${cellSize}px)`,
          borderRadius: 6,
          overflow: 'hidden',
          border: '1px solid #2a2d27',
          boxShadow: '0 20px 60px rgba(0,0,0,0.5), inset 0 0 0 1px rgba(255,255,255,0.02)',
        }}
      >
        {Array.from({ length: 64 }).map((_, i) => {
          const r = Math.floor(i / 8);
          const c = i % 8;
          const light = (r + c) % 2 === 0;
          const isFrom = highlight && highlight.from[0] === r && highlight.from[1] === c;
          const isTo = highlight && highlight.to[0] === r && highlight.to[1] === c;
          return (
            <div
              key={i}
              style={{
                position: 'relative',
                background: light ? '#3a3d33' : '#1f221c',
                transition: 'background 0.4s',
              }}
            >
              {isFrom && (
                <div style={{ position: 'absolute', inset: 0, background: 'rgba(106,191,116,0.18)', boxShadow: 'inset 0 0 0 1.5px rgba(106,191,116,0.5)' }} />
              )}
              {isTo && (
                <div style={{ position: 'absolute', inset: 0, background: 'rgba(106,191,116,0.32)', boxShadow: 'inset 0 0 0 1.5px #6abf74' }} />
              )}
            </div>
          );
        })}
      </div>
      <div
        style={{
          position: 'absolute',
          left: highlight ? `${highlight.to[1] * cellSize + cellSize / 2 - 14}px` : '50%',
          top: highlight ? `${highlight.to[0] * cellSize + cellSize / 2 - 20}px` : '50%',
          width: 28,
          height: 38,
          color: '#6abf74',
          filter: 'drop-shadow(0 4px 12px rgba(106,191,116,0.5))',
          transition: 'left 0.6s cubic-bezier(0.4, 0.0, 0.2, 1), top 0.6s cubic-bezier(0.4, 0.0, 0.2, 1)',
          pointerEvents: 'none',
        }}
      >
        <KnightSVG />
      </div>
    </div>
  );
};

/* ── Field ── */
interface FieldProps {
  label: string;
  type?: string;
  placeholder?: string;
  hint?: string;
  error?: string;
  value: string;
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
  name: string;
  autoComplete?: string;
  rightSlot?: ReactNode;
  autoFocus?: boolean;
}

const Field = ({ label, type = 'text', placeholder = '', hint = '', error = '', value, onChange, name, autoComplete, rightSlot, autoFocus }: FieldProps) => {
  const [focused, setFocused] = useState(false);
  const [show, setShow] = useState(false);
  const isPass = type === 'password';
  const descId = hint || error ? `${name}-desc` : undefined;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <label
          htmlFor={name}
          style={{
            fontSize: 10,
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            color: error ? '#e05a5a' : focused ? '#6abf74' : '#7a7d6e',
            fontFamily: 'Space Mono, monospace',
            transition: 'color 0.2s',
          }}
        >
          {label}
        </label>
        {rightSlot}
      </div>
      <div style={{ position: 'relative' }}>
        <input
          id={name}
          name={name}
          type={isPass ? (show ? 'text' : 'password') : type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          autoComplete={autoComplete}
          autoFocus={autoFocus}
          required
          aria-invalid={!!error}
          aria-describedby={descId}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          style={{
            width: '100%',
            background: '#0e100d',
            border: `1px solid ${error ? '#e05a5a' : focused ? '#4a7c59' : '#2a2d27'}`,
            borderRadius: 8,
            padding: isPass ? '12px 42px 12px 14px' : '12px 14px',
            color: '#e8ead4',
            fontSize: 14,
            fontFamily: 'inherit',
            outline: 'none',
            transition: 'border-color 0.2s, box-shadow 0.2s',
            boxShadow: focused ? '0 0 0 3px rgba(74,124,89,0.15)' : 'none',
          }}
        />
        {isPass && (
          <button
            type="button"
            onClick={() => setShow((s) => !s)}
            style={{
              position: 'absolute',
              right: 12,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              color: '#4a4d40',
              padding: 4,
              lineHeight: 0,
            }}
            aria-label={show ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          >
            {show ? (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                <line x1="1" y1="1" x2="23" y2="23" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                <circle cx="12" cy="12" r="3" />
              </svg>
            )}
          </button>
        )}
      </div>
      {(hint || error) && (
        <span
          id={descId}
          role={error ? 'alert' : undefined}
          style={{ fontSize: 11, color: error ? '#e05a5a' : '#4a4d40', lineHeight: 1.4 }}
        >
          {error || hint}
        </span>
      )}
    </div>
  );
};

/* ── Checkbox ── */
const Checkbox = ({ checked, onChange, children }: { checked: boolean; onChange: () => void; children: ReactNode }) => (
  <label style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', userSelect: 'none' }}>
    <div
      onClick={onChange}
      style={{
        width: 18,
        height: 18,
        borderRadius: 5,
        border: `1.5px solid ${checked ? '#6abf74' : '#2a2d27'}`,
        background: checked ? '#6abf74' : 'transparent',
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.2s',
        cursor: 'pointer',
      }}
    >
      {checked && (
        <svg width="10" height="8" viewBox="0 0 10 8" fill="none">
          <path d="M1 4L4 7L9 1" stroke="#0e100d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      )}
    </div>
    <span style={{ fontSize: 13, color: '#7a7d6e', lineHeight: 1.5 }}>{children}</span>
  </label>
);

/* ── Page ── */
export const LoginPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { login } = useAuth();
  const queryClient = useQueryClient();

  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [remember, setRemember] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const nextParam = params.get('next');
  const registerHref = useMemo(() => (nextParam ? `/register?next=${nextParam}` : '/register'), [nextParam]);

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setErrors((er) => ({ ...er, [e.target.name]: '', _form: '' }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!form.email.includes('@')) errs.email = 'Email inválido';
    if (form.password.length < 1) errs.password = 'Ingresa tu contraseña';
    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }

    setSubmitting(true);
    try {
      const u = await login(form.email.trim(), form.password);

      // ORGANIZER: enviar directo al panel del organizador en :5174 sin
      // pasar por el portal del jugador (cross-origin, no react-router).
      if (u.role === 'ORGANIZER') {
        const organizerUrl = `${window.location.protocol}//${window.location.hostname}:5174`;
        window.location.assign(organizerUrl);
        return;
      }

      // Prefetch del dashboard en paralelo a la navegación.
      queryClient.prefetchQuery({
        queryKey: ['me', 'playerId', u.supabaseUserId],
        queryFn: () => playerApi.dashboard().then((d) => d.profile?.id ?? null),
      });
      queryClient.prefetchQuery({
        queryKey: ['player', 'portal', 'dashboard'],
        queryFn: () => playerApi.dashboard(),
      });
      const decoded = nextParam ? decodeURIComponent(nextParam) : '';
      navigate(decoded && decoded.startsWith('/') ? decoded : '/');
    } catch (err) {
      const raw =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (err as { message?: string })?.message;
      const message = translateAuthError(raw, 'Credenciales inválidas');
      setErrors({ _form: message });
      // Refrescamos el campo de contraseña para que el usuario vuelva a tipear;
      // dejamos el email intacto para no obligar a reescribirlo.
      setForm((f) => ({ ...f, password: '' }));
    } finally {
      setSubmitting(false);
    }
  };

  const fontStack = "'Space Grotesk', system-ui, sans-serif";

  return (
    <div
      style={{
        display: 'flex',
        height: '100vh',
        overflow: 'hidden',
        background: '#111210',
        color: '#e8ead4',
        fontFamily: fontStack,
      }}
    >
      <style>{`
        @keyframes cq-spin { to { transform: rotate(360deg); } }
        @keyframes cq-slide-up { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
        @media (max-width: 880px) {
          .cq-login-left { display: none !important; }
          .cq-login-right { padding: 32px 22px !important; }
          .cq-login-help { position: static !important; margin-bottom: 20px; text-align: right; }
        }
      `}</style>

      {/* ── LEFT PANEL ── */}
      <div
        className="cq-login-left"
        style={{
          flex: '0 0 480px',
          background: '#181a17',
          borderRight: '1px solid #2a2d27',
          padding: '32px 40px',
          display: 'flex',
          flexDirection: 'column',
          gap: 24,
          overflowY: 'auto',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 28, height: 28, color: '#6abf74' }}>
            <ChessQueryLogo />
          </div>
          <span style={{ fontFamily: 'Space Mono, monospace', fontWeight: 700, fontSize: 15, letterSpacing: '0.05em' }}>
            ChessQuery
          </span>
        </div>

        <div>
          <p
            style={{
              fontSize: 11,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: '#4a4d40',
              fontFamily: 'Space Mono, monospace',
              marginBottom: 14,
            }}
          >
            ACCESO A LA PLATAFORMA
          </p>
          <h1 style={{ fontSize: 36, fontWeight: 700, lineHeight: 1.1, letterSpacing: '-0.02em', margin: 0 }}>
            Vuelve al
            <br />
            tablero,
            <br />
            <span style={{ color: '#6abf74' }}>la partida</span>
            <br />
            te espera.
          </h1>
          <p style={{ marginTop: 12, fontSize: 13, color: '#7a7d6e', lineHeight: 1.6 }}>
            Tu dashboard, rankings, torneos y análisis siguen donde los dejaste.
          </p>
        </div>

        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <ChessBackdrop />
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: 1,
            background: '#2a2d27',
            border: '1px solid #2a2d27',
            borderRadius: 10,
            overflow: 'hidden',
          }}
        >
          {[
            { label: 'JUGADORES', val: '12.4k' },
            { label: 'TORNEOS', val: '380' },
            { label: 'PARTIDAS', val: '2.1M' },
          ].map((s) => (
            <div key={s.label} style={{ background: '#181a17', padding: '12px 10px', textAlign: 'center' }}>
              <div style={{ fontSize: 18, fontWeight: 700, color: '#6abf74', fontFamily: 'Space Mono, monospace' }}>
                {s.val}
              </div>
              <div style={{ fontSize: 9, color: '#4a4d40', fontFamily: 'Space Mono, monospace', letterSpacing: '0.1em', marginTop: 2 }}>
                {s.label}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* ── RIGHT PANEL ── */}
      <div
        className="cq-login-right"
        style={{
          flex: 1,
          background: '#141614',
          padding: '32px 52px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          overflowY: 'auto',
          position: 'relative',
        }}
      >
        <div className="cq-login-help" style={{ position: 'absolute', top: 36, right: 40, fontSize: 13, color: '#7a7d6e' }}>
          ¿No tienes cuenta?{' '}
          <Link to={registerHref} style={{ color: '#6abf74', fontWeight: 600, textDecoration: 'none' }}>
            Crear una →
          </Link>
        </div>

        <div style={{ maxWidth: 420, width: '100%', margin: '0 auto', animation: 'cq-slide-up 0.4s ease' }}>
          <p
            style={{
              fontSize: 11,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: '#4a4d40',
              fontFamily: 'Space Mono, monospace',
              marginBottom: 8,
            }}
          >
            INICIAR SESIÓN
          </p>
          <h2 style={{ fontSize: 30, fontWeight: 700, letterSpacing: '-0.02em', marginBottom: 4 }}>
            Ingresa a tu cuenta
          </h2>
          <p style={{ fontSize: 13, color: '#7a7d6e', marginBottom: 20 }}>
            Continúa con tu progreso, rankings y torneos en ChessQuery.
          </p>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field
              label="Email"
              type="email"
              name="email"
              autoComplete="email"
              autoFocus
              value={form.email}
              onChange={handleChange}
              error={errors.email}
              placeholder="tu@email.com"
            />

            <Field
              label="Contraseña"
              type="password"
              name="password"
              autoComplete="current-password"
              value={form.password}
              onChange={handleChange}
              error={errors.password}
              placeholder="••••••••"
              rightSlot={
                <Link
                  to="/login"
                  onClick={(e) => e.preventDefault()}
                  style={{
                    fontSize: 11,
                    color: '#6abf74',
                    textDecoration: 'none',
                    fontFamily: 'Space Mono, monospace',
                    letterSpacing: '0.04em',
                  }}
                  title="Funcionalidad de recuperación de contraseña próximamente"
                >
                  ¿Olvidaste?
                </Link>
              }
            />

            {errors._form && (
              <div
                role="alert"
                style={{
                  background: 'rgba(224,90,90,0.08)',
                  border: '1px solid rgba(224,90,90,0.3)',
                  borderRadius: 8,
                  padding: '10px 12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#e05a5a" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
                <span style={{ fontSize: 12, color: '#e05a5a' }}>{errors._form}</span>
              </div>
            )}

            <Checkbox checked={remember} onChange={() => setRemember((r) => !r)}>
              Mantener sesión iniciada en este dispositivo
            </Checkbox>

            <button
              type="submit"
              disabled={submitting}
              style={{
                width: '100%',
                padding: '13px',
                marginTop: 4,
                background: submitting ? '#3d8a4a' : '#6abf74',
                color: '#0a100a',
                borderRadius: 10,
                border: 'none',
                fontFamily: 'inherit',
                fontWeight: 700,
                fontSize: 15,
                cursor: submitting ? 'wait' : 'pointer',
                letterSpacing: '-0.01em',
                transition: 'background 0.2s, transform 0.1s',
                boxShadow: '0 4px 20px rgba(106,191,116,0.25)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 10,
              }}
              onMouseEnter={(e) => {
                if (!submitting) e.currentTarget.style.background = '#7ece86';
              }}
              onMouseLeave={(e) => {
                if (!submitting) e.currentTarget.style.background = '#6abf74';
              }}
              onMouseDown={(e) => {
                if (!submitting) e.currentTarget.style.transform = 'scale(0.99)';
              }}
              onMouseUp={(e) => {
                if (!submitting) e.currentTarget.style.transform = 'scale(1)';
              }}
            >
              {submitting ? (
                <>
                  <svg
                    width="14"
                    height="14"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="#0a100a"
                    strokeWidth="3"
                    style={{ animation: 'cq-spin 0.8s linear infinite' }}
                  >
                    <path d="M21 12a9 9 0 1 1-6.2-8.55" />
                  </svg>
                  Verificando…
                </>
              ) : (
                'Iniciar sesión'
              )}
            </button>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginTop: 6,
                paddingTop: 14,
                borderTop: '1px solid #2a2d27',
              }}
            >
              <Link
                to="/"
                style={{
                  fontSize: 11,
                  color: '#7a7d6e',
                  fontFamily: 'Space Mono, monospace',
                  letterSpacing: '0.06em',
                  textDecoration: 'none',
                }}
              >
                ← INICIO
              </Link>
              <a
                href="https://github.com/anthropics"
                onClick={(e) => e.preventDefault()}
                style={{
                  fontSize: 11,
                  color: '#7a7d6e',
                  fontFamily: 'Space Mono, monospace',
                  letterSpacing: '0.06em',
                  textDecoration: 'none',
                }}
              >
                AYUDA →
              </a>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
