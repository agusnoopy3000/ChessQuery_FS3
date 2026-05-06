import { createSupabaseClient } from '@chessquery/shared';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL as string;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string;

/**
 * Cliente Supabase compartido para chess-portal. Importar desde aquí en
 * vez de instanciar múltiples clientes (evita conflictos de sesión en
 * localStorage).
 */
export const supabase = createSupabaseClient(supabaseUrl, supabaseAnonKey);
