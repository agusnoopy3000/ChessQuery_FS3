import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { Session, SupabaseClient, User } from '@supabase/supabase-js';
import { AuthUser, Role } from './types';

interface AuthContextValue {
  user: AuthUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  register: (input: RegisterInput) => Promise<AuthUser>;
}

export interface RegisterInput {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role?: Role;
  /** Username de Lichess opcional (solo PLAYER). */
  lichessUsername?: string;
  /** Nombre del club si el usuario lo introduce en el form. */
  clubName?: string;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface AuthProviderProps {
  /** Cliente Supabase compartido (creado en main.tsx con anon key). */
  supabase: SupabaseClient;
  /** Role por defecto al registrar (PLAYER en chess-portal, ORGANIZER en organizer-panel). */
  defaultRole?: Role;
  children: ReactNode;
}

/**
 * Mapea un usuario y sesión de Supabase al modelo `AuthUser` de la app.
 * - id: numérico (BIGINT de PLAYER) — se resuelve vía /users/by-supabase-id
 *       en una capa superior; aquí dejamos 0 hasta que esté disponible.
 * - role: leído de user_metadata.role (default PLAYER).
 */
function userFromSession(session: Session | null): AuthUser | null {
  if (!session?.user) return null;
  return userFromSupabaseUser(session.user);
}

function userFromSupabaseUser(u: User): AuthUser {
  const meta = (u.user_metadata ?? {}) as Record<string, unknown>;
  const role = (typeof meta.role === 'string' ? meta.role : 'PLAYER') as Role;
  return {
    id: 0,
    supabaseUserId: u.id,
    email: u.email ?? '',
    role,
    name: typeof meta.firstName === 'string' && typeof meta.lastName === 'string'
      ? `${meta.firstName} ${meta.lastName}`
      : undefined,
  };
}

export const AuthProvider = ({ supabase, defaultRole = 'PLAYER', children }: AuthProviderProps) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    supabase.auth.getSession().then(({ data }) => {
      if (!mounted) return;
      setUser(userFromSession(data.session));
      setLoading(false);
    });
    const { data: sub } = supabase.auth.onAuthStateChange((_event, session) => {
      setUser(userFromSession(session));
    });
    return () => {
      mounted = false;
      sub.subscription.unsubscribe();
    };
  }, [supabase]);

  const login = useCallback<AuthContextValue['login']>(
    async (email, password) => {
      const { data, error } = await supabase.auth.signInWithPassword({ email, password });
      if (error) throw error;
      const u = userFromSession(data.session);
      if (!u) throw new Error('Login no devolvió sesión');
      setUser(u);
      return u;
    },
    [supabase],
  );

  const register = useCallback<AuthContextValue['register']>(
    async (input) => {
      const role = input.role ?? defaultRole;
      const metadata: Record<string, string> = {
        role,
        firstName: input.firstName,
        lastName: input.lastName,
      };
      if (input.lichessUsername?.trim()) {
        metadata.lichessUsername = input.lichessUsername.trim();
      }
      if (input.clubName?.trim()) {
        metadata.clubName = input.clubName.trim();
      }
      const { data, error } = await supabase.auth.signUp({
        email: input.email,
        password: input.password,
        options: { data: metadata },
      });
      if (error) throw error;
      // Si la confirmación de email está activada, no hay session aún.
      // Iniciamos sesión explícitamente para mantener el flujo previo.
      if (!data.session) {
        return login(input.email, input.password);
      }
      const u = userFromSession(data.session);
      if (!u) throw new Error('Registro no devolvió sesión');
      setUser(u);
      return u;
    },
    [supabase, defaultRole, login],
  );

  const logout = useCallback<AuthContextValue['logout']>(async () => {
    await supabase.auth.signOut();
    setUser(null);
  }, [supabase]);

  const value = useMemo(
    () => ({ user, loading, login, logout, register }),
    [user, loading, login, logout, register],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextValue => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
};
