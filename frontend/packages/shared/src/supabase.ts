import { createClient, SupabaseClient } from '@supabase/supabase-js';

/**
 * Crea un cliente Supabase para autenticación. El cliente persiste la sesión
 * en localStorage automáticamente y refresca el access token en segundo plano.
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
  return createClient(url, anonKey, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: false,
    },
  });
};

export type { SupabaseClient } from '@supabase/supabase-js';
