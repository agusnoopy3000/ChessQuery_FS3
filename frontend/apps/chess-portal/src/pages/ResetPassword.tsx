import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { translateAuthError } from '@chessquery/shared';
import { supabase } from '../lib/supabase';

/**
 * Página destino del email de recuperación. Establece la sesión de recovery a
 * partir de la URL (el cliente compartido usa detectSessionInUrl:false, así que
 * lo hacemos a mano) y permite definir una nueva contraseña.
 *
 * Soporta los dos formatos que puede mandar Supabase:
 *  - implícito: #access_token=...&refresh_token=...&type=recovery
 *  - PKCE:      ?code=...
 */
export const ResetPasswordPage = () => {
  const navigate = useNavigate();
  const [ready, setReady] = useState(false);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    let active = true;
    const establish = async () => {
      try {
        const hash = new URLSearchParams(window.location.hash.replace(/^#/, ''));
        const accessToken = hash.get('access_token');
        const refreshToken = hash.get('refresh_token');
        const query = new URLSearchParams(window.location.search);
        const code = query.get('code');

        if (accessToken && refreshToken) {
          const { error: err } = await supabase.auth.setSession({ access_token: accessToken, refresh_token: refreshToken });
          if (err) throw err;
        } else if (code) {
          const { error: err } = await supabase.auth.exchangeCodeForSession(code);
          if (err) throw err;
        } else {
          // Quizá ya hay sesión de recovery activa
          const { data } = await supabase.auth.getSession();
          if (!data.session) throw new Error('Enlace inválido o expirado');
        }
        if (active) setReady(true);
      } catch (err) {
        if (active) setLinkError(translateAuthError((err as { message?: string })?.message, 'El enlace es inválido o expiró. Solicitá uno nuevo.'));
      }
    };
    establish();
    return () => { active = false; };
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    if (password.length < 8) { setError('Mínimo 8 caracteres'); return; }
    if (password !== confirm) { setError('Las contraseñas no coinciden'); return; }
    setSubmitting(true);
    try {
      const { error: err } = await supabase.auth.updateUser({ password });
      if (err) throw err;
      setDone(true);
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(translateAuthError((err as { message?: string })?.message, 'No se pudo actualizar la contraseña'));
    } finally {
      setSubmitting(false);
    }
  };

  const inputStyle = (hasError: boolean) => ({
    width: '100%', background: '#0e100d', border: `1px solid ${hasError ? '#e05a5a' : '#2a2d27'}`,
    borderRadius: 8, padding: '12px 14px', color: '#e8ead4', fontSize: 14, fontFamily: 'inherit', outline: 'none',
  });

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#111210', color: '#e8ead4', fontFamily: "'Space Grotesk', system-ui, sans-serif", padding: 20 }}>
      <div style={{ width: '100%', maxWidth: 400, background: '#181a17', border: '1px solid #2a2d27', borderRadius: 14, padding: 28 }}>
        <h1 style={{ fontSize: 22, fontWeight: 700, margin: '0 0 6px' }}>Nueva contraseña</h1>

        {linkError ? (
          <>
            <div role="alert" style={{ background: 'rgba(224,90,90,0.08)', border: '1px solid rgba(224,90,90,0.3)', borderRadius: 8, padding: '12px 14px', fontSize: 13, color: '#e05a5a' }}>
              {linkError}
            </div>
            <p style={{ textAlign: 'center', fontSize: 13, marginTop: 16 }}>
              <Link to="/forgot-password" style={{ color: '#6abf74', fontWeight: 600, textDecoration: 'none' }}>Solicitar un nuevo enlace</Link>
            </p>
          </>
        ) : done ? (
          <div role="alert" style={{ background: 'rgba(106,191,116,0.08)', border: '1px solid rgba(106,191,116,0.3)', borderRadius: 8, padding: '12px 14px', fontSize: 13 }}>
            ✓ Contraseña actualizada. Redirigiéndote al inicio de sesión…
          </div>
        ) : !ready ? (
          <p style={{ fontSize: 13, color: '#7a7d6e' }}>Validando enlace…</p>
        ) : (
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <label htmlFor="password" style={{ fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase', color: '#7a7d6e', fontFamily: 'Space Mono, monospace' }}>Nueva contraseña</label>
            <input id="password" name="password" type="password" autoComplete="new-password" autoFocus value={password} onChange={(e) => { setPassword(e.target.value); setError(null); }} style={inputStyle(!!error)} />
            <label htmlFor="confirm" style={{ fontSize: 10, letterSpacing: '0.12em', textTransform: 'uppercase', color: '#7a7d6e', fontFamily: 'Space Mono, monospace' }}>Confirmar contraseña</label>
            <input id="confirm" name="confirm" type="password" autoComplete="new-password" value={confirm} onChange={(e) => { setConfirm(e.target.value); setError(null); }} aria-describedby={error ? 'pw-error' : undefined} style={inputStyle(!!error)} />
            {error && <span id="pw-error" role="alert" style={{ fontSize: 12, color: '#e05a5a' }}>{error}</span>}
            <button type="submit" disabled={submitting} style={{ width: '100%', padding: 12, marginTop: 4, background: submitting ? '#3d8a4a' : '#6abf74', color: '#0a100a', borderRadius: 10, border: 'none', fontWeight: 700, fontSize: 15, cursor: submitting ? 'wait' : 'pointer', fontFamily: 'inherit' }}>
              {submitting ? 'Guardando…' : 'Actualizar contraseña'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
