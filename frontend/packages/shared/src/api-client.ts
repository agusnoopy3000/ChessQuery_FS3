import axios, { AxiosInstance } from 'axios';
import type { SupabaseClient } from '@supabase/supabase-js';

/**
 * Storage abstracto para tokens. Mantenido por compatibilidad con código
 * legacy y como fallback cuando no hay un cliente Supabase configurado.
 *
 * @deprecated Con Supabase Auth la sesión la gestiona el SDK; usar
 * `createSupabaseApiClient` en su lugar.
 */
export interface TokenStorage {
  getAccess: () => string | null;
  getRefresh: () => string | null;
  setTokens: (access: string, refresh: string) => void;
  clear: () => void;
}

export const localStorageTokenStorage = (prefix = 'chessquery'): TokenStorage => ({
  getAccess: () => localStorage.getItem(`${prefix}.access`),
  getRefresh: () => localStorage.getItem(`${prefix}.refresh`),
  setTokens: (access, refresh) => {
    localStorage.setItem(`${prefix}.access`, access);
    localStorage.setItem(`${prefix}.refresh`, refresh);
  },
  clear: () => {
    localStorage.removeItem(`${prefix}.access`);
    localStorage.removeItem(`${prefix}.refresh`);
  },
});

export interface CreateSupabaseApiClientOptions {
  baseURL: string;
  supabase: SupabaseClient;
  onAuthFailure?: () => void;
}

/**
 * Cliente axios que adjunta el access token de Supabase en cada request.
 *
 * IMPORTANTE: cacheamos el token en memoria y lo refrescamos vía
 * onAuthStateChange en vez de llamar a `supabase.auth.getSession()` en cada
 * request. El SDK serializa internamente las llamadas a getSession() con
 * un mutex; con N requests paralelas (live move + polling de notif +
 * dashboard) eso forma una cola que puede pasar de 10s y disparar el
 * timeout del cliente.
 *
 * El refresh automático lo sigue gestionando el SDK (autoRefreshToken=true);
 * cuando rota el token recibimos un evento TOKEN_REFRESHED y actualizamos
 * el cache.
 *
 * En 401 NO hacemos signOut automático (era demasiado agresivo —
 * cualquier 401 transitorio cerraba la sesión completa). Solo notificamos
 * a la app vía onAuthFailure si está suscrita.
 */
export const createSupabaseApiClient = ({
  baseURL,
  supabase,
  onAuthFailure,
}: CreateSupabaseApiClientOptions): AxiosInstance => {
  const client = axios.create({ baseURL, timeout: 20000 });

  let cachedToken: string | null = null;

  // Inicializar cache con la sesión actual (si existe).
  void supabase.auth.getSession().then(({ data }) => {
    cachedToken = data.session?.access_token ?? null;
  });

  // Mantener cache actualizado con cualquier cambio de auth state.
  supabase.auth.onAuthStateChange((_event, session) => {
    cachedToken = session?.access_token ?? null;
  });

  client.interceptors.request.use((config) => {
    if (cachedToken) {
      config.headers.set('Authorization', `Bearer ${cachedToken}`);
    }
    return config;
  });

  // Retry automático en errores de red transitorios. Por defecto sólo métodos
  // seguros; las mutaciones live incluidas acá son idempotentes en ms-game.
  const RETRYABLE_CODES = new Set(['ECONNRESET', 'ERR_NETWORK', 'ECONNABORTED']);
  const RETRYABLE_METHODS = new Set(['get', 'head', 'options']);
  const RETRYABLE_LIVE_MUTATION = /^\/api\/player\/play\/live\/[^/]+\/(join|move|resign|draw|timeout)$/;
  client.interceptors.response.use(
    (r) => r,
    async (error) => {
      const cfg = error.config;
      const method = String(cfg?.method ?? 'get').toLowerCase();
      const url = String(cfg?.url ?? '');
      const canRetry = RETRYABLE_METHODS.has(method)
        || (method === 'post' && RETRYABLE_LIVE_MUTATION.test(url));
      const isNetworkError = !error.response && (
        RETRYABLE_CODES.has(error.code) ||
        /Network Error|reset|socket hang up/i.test(error.message ?? '')
      );
      if (isNetworkError && cfg && canRetry && !cfg._cqRetried) {
        cfg._cqRetried = true;
        await new Promise((r) => setTimeout(r, 250));
        return client(cfg);
      }
      if (error.response?.status === 401) {
        onAuthFailure?.();
      }
      throw error;
    },
  );

  return client;
};

// ── Legacy (MS-Auth) — preservado para rollback ─────────────────────────────

export interface CreateApiClientOptions {
  baseURL: string;
  storage: TokenStorage;
  onAuthFailure?: () => void;
}

/**
 * @deprecated Cliente legacy contra MS-Auth. Usar `createSupabaseApiClient`.
 * Se mantiene para facilitar rollback (ver docs/ROLLBACK.md).
 */
export const createApiClient = ({ baseURL, storage, onAuthFailure }: CreateApiClientOptions): AxiosInstance => {
  const client = axios.create({ baseURL, timeout: 10000 });

  client.interceptors.request.use((config) => {
    const access = storage.getAccess();
    if (access) {
      config.headers.set('Authorization', `Bearer ${access}`);
    }
    return config;
  });

  client.interceptors.response.use(
    (r) => r,
    async (error) => {
      if (error.response?.status === 401) {
        storage.clear();
        onAuthFailure?.();
      }
      throw error;
    },
  );

  return client;
};
