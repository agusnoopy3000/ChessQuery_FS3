import { AxiosInstance } from 'axios';
import {
  createApiClient,
  localStorageTokenStorage,
  AdminDashboard,
  EtlStatus,
  EtlLogEntry,
  Pagination,
  Player,
} from '@chessquery/shared';

const baseURL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8080';
export const storage = localStorageTokenStorage('chessquery.admin');

export const api: AxiosInstance = createApiClient({
  baseURL,
  storage,
  onAuthFailure: () => {
    if (window.location.pathname !== '/login') window.location.assign('/login');
  },
});

export const adminApi = {
  dashboard: (): Promise<AdminDashboard> =>
    api.get('/api/admin/dashboard').then((r) => r.data),
  etlStatus: (): Promise<EtlStatus> =>
    api.get('/api/admin/etl/status').then((r) => r.data),
  etlLogs: (): Promise<EtlLogEntry[]> =>
    api.get('/api/admin/etl/logs').then((r) => r.data).catch(() => []),
  triggerEtl: (source: string) =>
    api.post(`/api/admin/etl/sync/${source}`, {}).then((r) => r.data),
  users: (): Promise<Pagination<Player> | Player[]> =>
    api.get('/api/admin/users').then((r) => r.data),
};
