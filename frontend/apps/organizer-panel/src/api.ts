import axios, { AxiosInstance } from 'axios';
import {
  createApiClient,
  localStorageTokenStorage,
  Tournament,
  TournamentRegistration,
  Round,
  Pagination,
} from '@chessquery/shared';

const baseURL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8080';
export const storage = localStorageTokenStorage('chessquery.organizer');

export const api: AxiosInstance = createApiClient({
  baseURL,
  storage,
  onAuthFailure: () => {
    if (window.location.pathname !== '/login') window.location.assign('/login');
  },
});

export const organizerApi = {
  listTournaments: (): Promise<Pagination<Tournament>> =>
    api.get('/api/organizer/tournaments').then((r) => r.data),
  getTournament: (id: string | number): Promise<Tournament> =>
    api.get(`/api/organizer/tournaments/${id}`).then((r) => r.data),
  createTournament: (input: Partial<Tournament>) =>
    api.post('/api/organizer/tournaments', input).then((r) => r.data),
  getRound: (id: string | number, n: number): Promise<Round> =>
    api.get(`/api/organizer/tournaments/${id}/round/${n}`).then((r) => r.data),
  generateRound: (id: string | number, n: number) =>
    api.post(`/api/organizer/tournaments/${id}/rounds/${n}/generate`, {}).then((r) => r.data),
  setPairingResult: (pid: string | number, result: string) =>
    api.patch(`/api/organizer/pairings/${pid}/result`, { result }).then((r) => r.data),
  getStandings: (id: string | number): Promise<unknown> =>
    api.get(`/api/organizer/tournaments/${id}/standings`).then((r) => r.data),
  listRegistrations: (id: string | number): Promise<TournamentRegistration[]> =>
    api.get(`/api/organizer/tournaments/${id}/registrations`).then((r) => r.data).catch(() => []),
};

export const authApi = {
  login: (email: string, password: string) =>
    axios.post(`${baseURL}/auth/login`, { email, password }).then((r) => r.data),
};
