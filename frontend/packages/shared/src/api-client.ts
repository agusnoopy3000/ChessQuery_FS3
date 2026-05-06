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
 * El refresh lo gestiona el propio Supabase Client (autoRefreshToken=true);
 * en caso de 401 invocamos onAuthFailure para que la app redirija a /login.
 */
export const createSupabaseApiClient = ({
  baseURL,
  supabase,
  onAuthFailure,
}: CreateSupabaseApiClientOptions): AxiosInstance => {
  const client = axios.create({ baseURL, timeout: 10000 });

  client.interceptors.request.use(async (config) => {
    const { data } = await supabase.auth.getSession();
    const token = data.session?.access_token;
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`);
    }
    return config;
  });

  client.interceptors.response.use(
    (r) => r,
    async (error) => {
      if (error.response?.status === 401) {
        await supabase.auth.signOut();
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
