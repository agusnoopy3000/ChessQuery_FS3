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
  return createClient(url, anonKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: false,
      storage,
      // sessionStorage no dispara `storage` events cross-tab, pero por
      // las dudas desactivamos cualquier multi-tab sync explícito.
      storageKey: 'chessquery-auth',
    },
  });
};

export type { SupabaseClient } from '@supabase/supabase-js';
