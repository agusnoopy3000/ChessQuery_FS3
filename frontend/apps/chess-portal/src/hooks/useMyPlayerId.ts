import { useQuery } from '@tanstack/react-query';
import { useAuth } from '@chessquery/shared';
import { playerApi } from '../api';

/**
 * Resuelve el `player.id` numérico del usuario autenticado vía
 * `playerApi.dashboard()` y lo cachea con react-query (key=supabaseUserId).
 *
 * El AuthContext de Supabase deja `user.id=0` porque no hace la traducción
 * UUID→player.id. Este hook centraliza esa resolución para que cualquier
 * componente la consuma sin volver a pedirla.
 */
export function useMyPlayerId(): number | null {
  const { user } = useAuth();
  const supabaseUserId = user?.supabaseUserId;
  const { data } = useQuery({
    queryKey: ['me', 'playerId', supabaseUserId],
    queryFn: () => playerApi.dashboard().then((d) => d.profile?.id ?? null),
    enabled: !!supabaseUserId,
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
  });
  return data ?? null;
}
