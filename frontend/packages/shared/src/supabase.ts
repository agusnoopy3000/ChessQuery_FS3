import { createClient, SupabaseClient } from '@supabase/supabase-js';

/**
 * Crea un cliente Supabase para autenticación.
 *
 * IMPORTANTE: usamos `sessionStorage` (no el default `localStorage`) para
 * que cada pestaña del navegador tenga su propia sesión. Sin esto, hacer
 * login con un segundo usuario en otra pestaña pisa al primero (caso de
 * uso típico de la demo: jugar Ana vs Bruno en dos tabs).
 *
 * Trade-off: cerrar la pestaña pierde la sesión (hay que re-loguear).
 * Para un demo / app de un solo dispositivo es lo correcto.
 *
 * Las apps deben llamar esto una sola vez en main.tsx con las variables
 * VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY definidas en .env.
 */
export const createSupabaseClient = (url: string, anonKey: string): SupabaseClient => {
  if (!url || !anonKey) {
    throw new Error(
      'createSupabaseClient: VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY son obligatorios',
    );
  }
  // Guard: en SSR no hay sessionStorage, usar el default (no-op storage).
  const storage = typeof window !== 'undefined' && window.sessionStorage
    ? window.sessionStorage
    : undefined;

  // Aislamiento real cross-tab:
  // - sessionStorage por sí solo NO alcanza, porque supabase-js abre un
  //   BroadcastChannel cuyo nombre es el storageKey. Si dos tabs comparten
  //   storageKey, el SIGNED_IN de una propaga el sub/jwt nuevo a la otra
  //   y pisa la sesión del primer usuario.
  // - Generamos un storageKey único por pestaña (random) y lo guardamos
  //   en sessionStorage para que sobreviva reloads de la misma tab.
  let storageKey = 'chessquery-auth';
  if (typeof window !== 'undefined' && window.sessionStorage) {
    const existing = window.sessionStorage.getItem('cq-auth-tab-key');
    if (existing) {
      storageKey = existing;
    } else {
      storageKey = `chessquery-auth-${Math.random().toString(36).slice(2, 10)}`;
      window.sessionStorage.setItem('cq-auth-tab-key', storageKey);
    }
  }

  return createClient(url, anonKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: false,
      storage,
      storageKey,
    },
  });
};

export type { SupabaseClient } from '@supabase/supabase-js';
