import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';

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

export interface CreateApiClientOptions {
  baseURL: string;
  storage: TokenStorage;
  onAuthFailure?: () => void;
}

type RetryConfig = InternalAxiosRequestConfig & { __retry?: boolean };

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
      const original = error.config as RetryConfig | undefined;
      if (!original || error.response?.status !== 401 || original.__retry) {
        throw error;
      }

      // Don't try to refresh the refresh-call itself.
      if (original.url?.endsWith('/auth/refresh')) {
        storage.clear();
        onAuthFailure?.();
        throw error;
      }

      const refresh = storage.getRefresh();
      if (!refresh) {
        storage.clear();
        onAuthFailure?.();
        throw error;
      }

      try {
        original.__retry = true;
        const r = await axios.post(`${baseURL}/auth/refresh`, { refreshToken: refresh });
        const access = r.data?.accessToken;
        const newRefresh = r.data?.refreshToken ?? refresh;
        if (!access) throw new Error('no accessToken in refresh response');
        storage.setTokens(access, newRefresh);
        original.headers.set('Authorization', `Bearer ${access}`);
        return client(original);
      } catch (e) {
        storage.clear();
        onAuthFailure?.();
        throw e;
      }
    },
  );

  return client;
};
