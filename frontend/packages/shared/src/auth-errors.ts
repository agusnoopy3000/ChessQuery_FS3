/**
 * Traduce los mensajes de error que devuelve Supabase Auth (o cualquier
 * backend que use mensajes en inglés) a español, presentables al usuario.
 *
 * Cualquier error que no matchee patrones conocidos cae al `defaultMessage`
 * pasado por el caller (p.ej. "Credenciales inválidas" o "No se pudo crear
 * la cuenta") para que la UI nunca muestre texto en inglés crudo.
 */
const PATTERNS: Array<{ regex: RegExp; spanish: string }> = [
  { regex: /invalid login credentials/i, spanish: 'Email o contraseña incorrectos' },
  { regex: /email not confirmed/i, spanish: 'Debes confirmar tu email antes de ingresar' },
  { regex: /user already registered/i, spanish: 'Este email ya está registrado' },
  { regex: /already been registered/i, spanish: 'Este email ya está registrado' },
  { regex: /user not found/i, spanish: 'No existe una cuenta con ese email' },
  { regex: /password should be at least (\d+)/i, spanish: 'La contraseña debe tener al menos $1 caracteres' },
  { regex: /password is too short/i, spanish: 'La contraseña es demasiado corta' },
  { regex: /signup requires a valid password/i, spanish: 'La contraseña no es válida' },
  { regex: /weak password/i, spanish: 'La contraseña es muy débil' },
  { regex: /invalid email|invalid format.*email|unable to validate email/i, spanish: 'Formato de email inválido' },
  { regex: /email rate limit exceeded|over_email_send_rate_limit/i, spanish: 'Demasiados intentos. Espera unos minutos e intenta de nuevo' },
  { regex: /rate limit exceeded|too many requests/i, spanish: 'Demasiados intentos. Espera un momento' },
  { regex: /network error|fetch failed|network request failed/i, spanish: 'No se pudo conectar con el servidor. Revisa tu conexión' },
  { regex: /timeout|timed? out/i, spanish: 'La operación tardó demasiado. Intenta de nuevo' },
  { regex: /signups? (are )?disabled|signup is disabled/i, spanish: 'El registro está temporalmente deshabilitado' },
];

export const translateAuthError = (
  rawMessage: string | undefined | null,
  defaultMessage: string,
): string => {
  if (!rawMessage) return defaultMessage;
  for (const { regex, spanish } of PATTERNS) {
    const match = rawMessage.match(regex);
    if (match) {
      return spanish.replace('$1', match[1] ?? '');
    }
  }
  // Si el mensaje ya está en español (contiene caracteres específicos o palabras),
  // lo devolvemos tal cual. Detectamos heurísticamente.
  if (/[áéíóúñ¿¡]|contrase|inváli|incorrect|requer/i.test(rawMessage)) {
    return rawMessage;
  }
  return defaultMessage;
};
