import { useState, ChangeEvent, FormEvent, ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Role, useAuth } from '@chessquery/shared';

/* ── Logo SVG ── */
const ChessQueryLogo = () => (
  <svg viewBox="0 0 28 28" fill="none" style={{ width: '100%', height: '100%' }}>
    <rect width="28" height="28" rx="6" fill="currentColor" opacity=".15" />
    <path d="M8 22v-2.5H6V16h2v-2h2v-2h4v2h2v2h2v3.5h-2V22H8z" fill="currentColor" />
    <rect x="11" y="6" width="2.5" height="5" rx="1.25" fill="currentColor" />
    <rect x="8" y="8" width="8" height="2.5" rx="1.25" fill="currentColor" />
  </svg>
);

/* ── Chess SVG pieces ── */
const PawnSVG = () => (
  <svg viewBox="0 0 40 56" fill="none" style={{ width: '100%', height: '100%' }}>
    <ellipse cx="20" cy="50" rx="14" ry="4" fill="currentColor" opacity=".25" />
    <rect x="11" y="42" width="18" height="6" rx="3" fill="currentColor" />
    <path d="M15 42 L13 34 Q12 28 20 24 Q28 28 27 34 L25 42Z" fill="currentColor" />
    <circle cx="20" cy="16" r="10" fill="currentColor" />
    <ellipse cx="20" cy="16" rx="6" ry="6" fill="currentColor" opacity=".3" />
  </svg>
);

const KingSVG = () => (
  <svg viewBox="0 0 40 60" fill="none" style={{ width: '100%', height: '100%' }}>
    <ellipse cx="20" cy="54" rx="15" ry="4" fill="currentColor" opacity=".2" />
    <rect x="9" y="44" width="22" height="8" rx="3" fill="currentColor" />
    <path d="M12 44 L11 28 Q10 20 20 18 Q30 20 29 28 L28 44Z" fill="currentColor" />
    <rect x="17" y="6" width="6" height="16" rx="3" fill="currentColor" />
    <rect x="11" y="10" width="18" height="6" rx="3" fill="currentColor" />
    <circle cx="20" cy="4" r="4" fill="currentColor" />
  </svg>
);

/* ── Role visual: Player (mini board + pawn + stats) ── */
const PlayerVisual = ({ selected }: { selected: boolean }) => (
  <div
    style={{
      position: 'relative',
      borderRadius: 12,
      overflow: 'hidden',
      background: selected ? 'rgba(106,191,116,0.06)' : 'rgba(255,255,255,0.03)',
      border: `1px solid ${selected ? 'rgba(106,191,116,0.2)' : 'rgba(255,255,255,0.05)'}`,
      height: 100,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 16,
      padding: '0 16px',
    }}
  >
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,14px)', gridTemplateRows: 'repeat(4,14px)', gap: 1, flexShrink: 0 }}>
      {Array.from({ length: 16 }).map((_, i) => {
        const r = Math.floor(i / 4);
        const c = i % 4;
        const light = (r + c) % 2 === 0;
        return <div key={i} style={{ width: 14, height: 14, background: light ? '#d4b483' : '#7a5c2e', borderRadius: 2 }} />;
      })}
    </div>
    <div
      style={{
        color: selected ? '#6abf74' : '#7a7d6e',
        width: 36,
        height: 50,
        filter: selected ? 'drop-shadow(0 0 8px rgba(106,191,116,0.5))' : 'none',
        transition: 'all 0.3s',
      }}
    >
      <PawnSVG />
    </div>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
      {[
        { label: 'ELO', val: '1842' },
        { label: 'Partidas', val: '247' },
        { label: 'Victorias', val: '61%' },
      ].map((s) => (
        <div key={s.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 9, color: '#4a4d40', fontFamily: 'Space Mono, monospace', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
            {s.label}
          </span>
          <span style={{ fontSize: 11, color: selected ? '#6abf74' : '#7a7d6e', fontFamily: 'Space Mono, monospace', fontWeight: 700 }}>
            {s.val}
          </span>
        </div>
      ))}
    </div>
  </div>
);

/* ── Role visual: Organizer (bracket + king + stats) ── */
const OrganizerVisual = ({ selected }: { selected: boolean }) => {
  const stroke = selected ? '#6abf74' : '#3a3d30';
  return (
    <div
      style={{
        position: 'relative',
        borderRadius: 12,
        overflow: 'hidden',
        background: selected ? 'rgba(106,191,116,0.06)' : 'rgba(255,255,255,0.03)',
        border: `1px solid ${selected ? 'rgba(106,191,116,0.2)' : 'rgba(255,255,255,0.05)'}`,
        height: 100,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: '0 14px',
      }}
    >
      <div style={{ position: 'relative', width: 60, height: 70, flexShrink: 0 }}>
        <svg width="60" height="70" viewBox="0 0 60 70" fill="none">
          <line x1="4" y1="12" x2="28" y2="12" stroke={stroke} strokeWidth="1.5" />
          <line x1="4" y1="35" x2="28" y2="35" stroke={stroke} strokeWidth="1.5" />
          <line x1="4" y1="58" x2="28" y2="58" stroke={stroke} strokeWidth="1.5" />
          <line x1="28" y1="12" x2="28" y2="35" stroke={stroke} strokeWidth="1.5" />
          <line x1="28" y1="58" x2="28" y2="47" stroke={stroke} strokeWidth="1.5" />
          <line x1="28" y1="23" x2="48" y2="23" stroke={stroke} strokeWidth="1.5" />
          <line x1="28" y1="47" x2="48" y2="47" stroke={stroke} strokeWidth="1.5" />
          <line x1="48" y1="23" x2="48" y2="47" stroke={stroke} strokeWidth="1.5" />
          <line x1="48" y1="35" x2="58" y2="35" stroke={stroke} strokeWidth="1.5" />
          <circle cx="4" cy="12" r="3" fill={stroke} />
          <circle cx="4" cy="35" r="3" fill={stroke} />
          <circle cx="4" cy="58" r="3" fill={selected ? '#3a3d30' : '#252820'} />
          <circle cx="48" cy="35" r="4" fill={stroke} stroke={selected ? '#b8f5c0' : '#555'} strokeWidth="1.5" />
        </svg>
      </div>
      <div
        style={{
          color: selected ? '#6abf74' : '#7a7d6e',
          width: 30,
          height: 42,
          filter: selected ? 'drop-shadow(0 0 8px rgba(106,191,116,0.5))' : 'none',
          transition: 'all 0.3s',
        }}
      >
        <KingSVG />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flex: 1 }}>
        {[
          { label: 'Torneos', val: '12' },
          { label: 'Jugadores', val: '380' },
          { label: 'Clubes', val: '3' },
        ].map((s) => (
          <div key={s.label} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: 9, color: '#4a4d40', fontFamily: 'Space Mono, monospace', textTransform: 'uppercase', letterSpacing: '0.08em' }}>
              {s.label}
            </span>
            <span style={{ fontSize: 11, color: selected ? '#6abf74' : '#7a7d6e', fontFamily: 'Space Mono, monospace', fontWeight: 700 }}>
              {s.val}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

/* ── Form field ── */
interface FieldProps {
  label: string;
  type?: string;
  placeholder?: string;
  hint?: string;
  error?: string;
  half?: boolean;
  value: string;
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
  name: string;
}
const Field = ({ label, type = 'text', placeholder = '', hint = '', error = '', half = false, value, onChange, name }: FieldProps) => {
  const [focused, setFocused] = useState(false);
  const [show, setShow] = useState(false);
  const isPass = type === 'password';
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 5,
        flex: half ? '1 1 calc(50% - 6px)' : '1 1 100%',
        minWidth: half ? 120 : 'auto',
      }}
    >
      <label
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
      <div style={{ position: 'relative' }}>
        <input
          name={name}
          type={isPass ? (show ? 'text' : 'password') : type}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          onFocus={() => setFocused(true)}
          onBlur={() => setFocused(false)}
          style={{
            width: '100%',
            background: '#0e100d',
            border: `1px solid ${error ? '#e05a5a' : focused ? '#4a7c59' : '#2a2d27'}`,
            borderRadius: 8,
            padding: isPass ? '10px 42px 10px 14px' : '10px 14px',
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
        <span style={{ fontSize: 11, color: error ? '#e05a5a' : '#4a4d40', lineHeight: 1.4 }}>{error || hint}</span>
      )}
    </div>
  );
};

/* ── Checkbox ── */
const Checkbox = ({ checked, onChange, children }: { checked: boolean; onChange: () => void; children: ReactNode }) => (
  <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, cursor: 'pointer', userSelect: 'none' }}>
    <div
      onClick={onChange}
      style={{
        width: 18,
        height: 18,
        borderRadius: 5,
        border: `1.5px solid ${checked ? '#6abf74' : '#2a2d27'}`,
        background: checked ? '#6abf74' : 'transparent',
        flexShrink: 0,
        marginTop: 1,
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
export const RegisterPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { register } = useAuth();
  const nextParam = params.get('next');
  const next = nextParam ? decodeURIComponent(nextParam) : '';

  const [role, setRole] = useState<Role>('PLAYER');
  const [form, setForm] = useState({
    nombre: '',
    apellido: '',
    email: '',
    password: '',
    confirmPassword: '',
    lichess: '',
    club: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [terms, setTerms] = useState(false);
  const [newsletter, setNewsletter] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setErrors((er) => ({ ...er, [e.target.name]: '' }));
  };

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!form.nombre.trim()) errs.nombre = 'Campo requerido';
    if (!form.apellido.trim()) errs.apellido = 'Campo requerido';
    if (!form.email.includes('@')) errs.email = 'Email inválido';
    if (form.password.length < 8) errs.password = 'Mínimo 8 caracteres';
    if (form.password !== form.confirmPassword) errs.confirmPassword = 'Las contraseñas no coinciden';
    if (!terms) errs.terms = 'Debes aceptar los términos para continuar';
    return errs;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError(null);
    const errs = validate();
    if (Object.keys(errs).length > 0) {
      setErrors(errs);
      return;
    }
    setSubmitting(true);
    try {
      await register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.nombre.trim(),
        lastName: form.apellido.trim(),
        role,
        lichessUsername: role === 'PLAYER' ? form.lichess.trim() || undefined : undefined,
        clubName: role === 'ORGANIZER' ? form.club.trim() || undefined : undefined,
      });
      // Si venía de un link de invitación (?next=/play/123), respetamos esa URL.
      // Si no, mandamos al dashboard correspondiente al rol elegido.
      if (next && next.startsWith('/')) {
        navigate(next);
      } else {
        navigate(role === 'ORGANIZER' ? '/' : '/portal');
      }
    } catch (err) {
      const msg =
        (err as { message?: string })?.message ??
        (err as { error_description?: string })?.error_description ??
        'No se pudo crear la cuenta';
      setServerError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const fontStack = "'Space Grotesk', system-ui, sans-serif";
  const submitLabel = role === 'PLAYER' ? 'Crear cuenta jugador' : 'Crear cuenta organizador';

  return (
    <div
      style={{
        display: 'flex',
        minHeight: '100vh',
        background: '#111210',
        color: '#e8ead4',
        fontFamily: fontStack,
      }}
    >
      {/* ── LEFT PANEL ── */}
      <div
        style={{
          flex: '0 0 480px',
          background: '#181a17',
          borderRight: '1px solid #2a2d27',
          padding: '48px 40px',
          display: 'flex',
          flexDirection: 'column',
          gap: 32,
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
            REGISTRO POR ROL
          </p>
          <h1 style={{ fontSize: 40, fontWeight: 700, lineHeight: 1.1, letterSpacing: '-0.02em', margin: 0 }}>
            Elige cómo
            <br />
            entrar al
            <br />
            ecosistema
            <br />
            <span style={{ color: '#6abf74' }}>ChessQuery.</span>
          </h1>
          <p style={{ marginTop: 20, fontSize: 14, color: '#7a7d6e', lineHeight: 1.6 }}>
            El rol que elijas define tu dashboard, permisos y cómo interactuás con la comunidad.
          </p>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <RoleCard
            selected={role === 'PLAYER'}
            title="Jugador"
            description="Compite, consulta rankings y juega desde tu portal."
            badge={role === 'PLAYER' ? 'SELECCIONADO' : 'DISPONIBLE'}
            visual={<PlayerVisual selected={role === 'PLAYER'} />}
            iconSvg={
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 2a4 4 0 1 0 0 8 4 4 0 0 0 0-8zm0 10c-5.33 0-8 2.67-8 4v2h16v-2c0-1.33-2.67-4-8-4z" />
              </svg>
            }
            onClick={() => setRole('PLAYER')}
          />
          <RoleCard
            selected={role === 'ORGANIZER'}
            title="Organizador"
            description="Gestiona clubes, academias, torneos y validación de perfiles."
            badge={role === 'ORGANIZER' ? 'SELECCIONADO' : 'DISPONIBLE'}
            visual={<OrganizerVisual selected={role === 'ORGANIZER'} />}
            iconSvg={
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <path d="M17.5 14v7M14 17.5h7" />
              </svg>
            }
            onClick={() => setRole('ORGANIZER')}
          />
        </div>
      </div>

      {/* ── RIGHT PANEL ── */}
      <div
        style={{
          flex: 1,
          background: '#141614',
          padding: '48px 52px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          overflowY: 'auto',
        }}
      >
        <div style={{ maxWidth: 480, width: '100%', margin: '0 auto' }}>
          <p
            style={{
              fontSize: 11,
              letterSpacing: '0.14em',
              textTransform: 'uppercase',
              color: '#4a4d40',
              fontFamily: 'Space Mono, monospace',
              marginBottom: 10,
            }}
          >
            NUEVO ACCESO
          </p>
          <h2 style={{ fontSize: 34, fontWeight: 700, letterSpacing: '-0.02em', marginBottom: 6 }}>Crear cuenta</h2>
          <p style={{ fontSize: 13, color: '#7a7d6e', marginBottom: 30 }}>
            <span style={{ color: '#6abf74', fontWeight: 600 }}>{role === 'PLAYER' ? 'Jugador' : 'Organizador'}</span>
            {role === 'PLAYER'
              ? ' · Compite, consulta rankings y juega desde tu portal.'
              : ' · Gestiona torneos, clubes y perfiles de jugadores.'}
          </p>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Field label="Nombre" name="nombre" value={form.nombre} onChange={handleChange} error={errors.nombre} half />
              <Field label="Apellido" name="apellido" value={form.apellido} onChange={handleChange} error={errors.apellido} half />
            </div>

            <Field label="Email" type="email" name="email" value={form.email} onChange={handleChange} error={errors.email} placeholder="tu@email.com" />
            <Field label="Contraseña" type="password" name="password" value={form.password} onChange={handleChange} error={errors.password} hint="Mínimo 8 caracteres" />
            <Field label="Confirmar contraseña" type="password" name="confirmPassword" value={form.confirmPassword} onChange={handleChange} error={errors.confirmPassword} />

            {role === 'PLAYER' ? (
              <Field
                label="Usuario de Lichess (opcional)"
                name="lichess"
                value={form.lichess}
                onChange={handleChange}
                placeholder="ej: DrNykterstein"
                hint="Permite mostrar tu ELO de plataforma por modalidad en tu perfil"
              />
            ) : (
              <Field
                label="Nombre del club u organización (opcional)"
                name="club"
                value={form.club}
                onChange={handleChange}
                placeholder="ej: Club Ajedrez Santiago"
                hint="Podés agregar más clubes luego desde tu dashboard"
              />
            )}

            <div style={{ height: 1, background: '#2a2d27', margin: '4px 0' }} />

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <Checkbox checked={terms} onChange={() => setTerms((t) => !t)}>
                  Acepto los{' '}
                  <a href="#" style={{ color: '#6abf74', textDecoration: 'underline', textUnderlineOffset: 3 }} onClick={(e) => e.preventDefault()}>
                    Términos y Condiciones
                  </a>{' '}
                  y la{' '}
                  <a href="#" style={{ color: '#6abf74', textDecoration: 'underline', textUnderlineOffset: 3 }} onClick={(e) => e.preventDefault()}>
                    Política de Privacidad
                  </a>{' '}
                  de ChessQuery.
                </Checkbox>
                {errors.terms && <p style={{ fontSize: 11, color: '#e05a5a', marginTop: 5, marginLeft: 28 }}>{errors.terms}</p>}
              </div>
              <Checkbox checked={newsletter} onChange={() => setNewsletter((n) => !n)}>
                Quiero recibir novedades, torneos y actualizaciones de ChessQuery (opcional).
              </Checkbox>
            </div>

            {serverError && (
              <div style={{ background: 'rgba(224,90,90,0.1)', border: '1px solid #e05a5a', borderRadius: 8, padding: '10px 14px', color: '#e05a5a', fontSize: 13 }}>
                {serverError}
              </div>
            )}

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
              }}
            >
              {submitting ? 'Creando cuenta…' : submitLabel}
            </button>

            <p style={{ textAlign: 'center', fontSize: 13, color: '#4a4d40' }}>
              ¿Ya tenés cuenta?{' '}
              <a
                href="/login"
                style={{ color: '#6abf74', fontWeight: 600, textDecoration: 'none' }}
                onClick={(e) => {
                  e.preventDefault();
                  navigate(nextParam ? `/login?next=${nextParam}` : '/login');
                }}
              >
                Iniciar sesión
              </a>
            </p>
          </form>
        </div>
      </div>
    </div>
  );
};

/* ── Helper: role card ── */
interface RoleCardProps {
  selected: boolean;
  title: string;
  description: string;
  badge: string;
  iconSvg: ReactNode;
  visual: ReactNode;
  onClick: () => void;
}
const RoleCard = ({ selected, title, description, badge, iconSvg, visual, onClick }: RoleCardProps) => (
  <div
    onClick={onClick}
    style={{
      borderRadius: 14,
      border: `1.5px solid ${selected ? '#4a7c59' : '#252820'}`,
      background: selected ? '#1e2b1f' : '#191c18',
      padding: 18,
      cursor: 'pointer',
      transition: 'all 0.25s',
      boxShadow: selected ? '0 0 0 1px rgba(106,191,116,0.1), inset 0 1px 0 rgba(106,191,116,0.07)' : 'none',
    }}
  >
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
      <div
        style={{
          width: 38,
          height: 38,
          borderRadius: 10,
          background: selected ? 'rgba(106,191,116,0.15)' : 'rgba(255,255,255,0.05)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: selected ? '#6abf74' : '#4a4d40',
        }}
      >
        {iconSvg}
      </div>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 700, fontSize: 16, color: '#e8ead4' }}>{title}</div>
        <div style={{ fontSize: 12, color: '#7a7d6e', marginTop: 2 }}>{description}</div>
      </div>
      <div
        style={{
          fontSize: 10,
          fontFamily: 'Space Mono, monospace',
          fontWeight: 700,
          letterSpacing: '0.08em',
          padding: '3px 10px',
          borderRadius: 20,
          background: selected ? 'rgba(106,191,116,0.15)' : 'rgba(255,255,255,0.05)',
          color: selected ? '#6abf74' : '#7a7d6e',
          border: selected ? '1px solid rgba(106,191,116,0.3)' : '1px solid transparent',
        }}
      >
        {badge}
      </div>
    </div>
    {visual}
  </div>
);
