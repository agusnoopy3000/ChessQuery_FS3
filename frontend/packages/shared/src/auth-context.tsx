import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { AxiosInstance } from 'axios';
import { AuthUser, LoginResponse, Role } from './types';
import { TokenStorage } from './api-client';

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
}

const AuthContext = createContext<AuthContextValue | null>(null);

export interface AuthProviderProps {
  client: AxiosInstance;
  storage: TokenStorage;
  children: ReactNode;
}

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split('.');
    if (parts.length < 2) return null;
    const payload = parts[1];
    const pad = payload.length % 4 === 0 ? 0 : 4 - (payload.length % 4);
    const decoded = atob(payload.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(pad));
    return JSON.parse(decoded);
  } catch {
    return null;
  }
}

function userFromAccessToken(token: string): AuthUser | null {
  const claims = decodeJwtPayload(token);
  if (!claims) return null;
  const userId = Number(claims.sub ?? claims.userId);
  const email = typeof claims.email === 'string' ? claims.email : '';
  const role = (claims.role ?? 'PLAYER') as Role;
  if (!userId || !email) return null;
  return { id: userId, email, role };
}

export const AuthProvider = ({ client, storage, children }: AuthProviderProps) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const access = storage.getAccess();
    if (access) {
      const u = userFromAccessToken(access);
      if (u) setUser(u);
    }
    setLoading(false);
  }, [storage]);

  const login = useCallback<AuthContextValue['login']>(
    async (email, password) => {
      const res = await client.post<LoginResponse>('/auth/login', { email, password });
      storage.setTokens(res.data.accessToken, res.data.refreshToken);
      const u: AuthUser = { id: res.data.userId, email: res.data.email, role: res.data.role };
      setUser(u);
      return u;
    },
    [client, storage],
  );

  const register = useCallback<AuthContextValue['register']>(
    async (input) => {
      await client.post('/auth/register', input);
      return login(input.email, input.password);
    },
    [client, login],
  );

  const logout = useCallback<AuthContextValue['logout']>(async () => {
    const refresh = storage.getRefresh();
    try {
      if (refresh) {
        await client.post('/auth/logout', { refreshToken: refresh });
      }
    } catch {
      /* ignore */
    } finally {
      storage.clear();
      setUser(null);
    }
  }, [client, storage]);

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
