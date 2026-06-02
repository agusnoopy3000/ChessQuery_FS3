import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { translateAuthError } from '@chessquery/shared';
import { supabase } from '../lib/supabase';

/**
 * Solicitud de recuperación de contraseña. Envía el email de reset de Supabase
 * con redirect a /reset-password. (Requiere envío de email configurado; con el
 * SMTP por defecto de Supabase hay rate limit.)
 */
export const ForgotPasswordPage = () => {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!email.includes('@')) {
      setError('Ingresá un email válido');
      return;
    }
    setSubmitting(true);
    try {
      const redirectTo = `${window.location.origin}/reset-password`;
      const { error: err } = await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo });
      if (err) throw err;
      setSent(true);
    } catch (err) {
      const raw = (err as { message?: string })?.message;
      setError(translateAuthError(raw, 'No se pudo enviar el email de recuperación'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#111210', color: '#e8ead4', fontFamily: "'Space Grotesk', system-ui, sans-serif", padding: 20 }}>
      <div style={{ width: '100%', maxWidth: 400, background: '#181a17', border: '1px solid #2a2d27', borderRadius: 14, padding: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, margin: '0 0 6px' }}>Recuperar contraseña</h1>
        <p style={{ fontSize: 13, color: '#7a7d6e', margin: '0 0 18px' }}>
          Te enviaremos un enlace para crear una nueva contraseña.
        </p>

        {sent ? (
          <div role="alert" style={{ background: 'rgba(106,191,116,0.08)', border: '1px solid rgba(106,191,116,0.3)', borderRadius: 8, padding: '12px 14px', fontSize: 13 }}>
            ✓ Si existe una cuenta con <strong>{email}</strong>, te llegó un email con el enlace para restablecer tu contraseña. Revisá tu bandeja (y spam).
          </div>
        ) : (
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <label htmlFor="email" style={{ fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase', color: '#7a7d6e', fontFamily: 'Space Mono, monospace' }}>
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              autoFocus
              value={email}
              onChange={(e) => { setEmail(e.target.value); setError(null); }}
              placeholder="tu@email.com"
              aria-invalid={!!error}
              aria-describedby={error ? 'email-error' : undefined}
              style={{ width: '100%', background: '#0e100d', border: `1px solid ${error ? '#e05a5a' : '#2a2d27'}`, borderRadius: 8, padding: '12px 14px', color: '#e8ead4', fontSize: 14, fontFamily: 'inherit', outline: 'none' }}
            />
            {error && <span id="email-error" role="alert" style={{ fontSize: 12, color: '#e05a5a' }}>{error}</span>}
            <button type="submit" disabled={submitting} style={{ width: '100%', padding: 12, marginTop: 4, background: submitting ? '#3d8a4a' : '#6abf74', color: '#0a100a', borderRadius: 10, border: 'none', fontWeight: 700, fontSize: 15, cursor: submitting ? 'wait' : 'pointer', fontFamily: 'inherit' }}>
              {submitting ? 'Enviando…' : 'Enviar enlace'}
            </button>
          </form>
        )}

        <p style={{ textAlign: 'center', fontSize: 13, color: '#4a4d40', marginTop: 16 }}>
          <Link to="/login" style={{ color: '#6abf74', fontWeight: 600, textDecoration: 'none' }}>← Volver a iniciar sesión</Link>
        </p>
      </div>
    </div>
  );
};
