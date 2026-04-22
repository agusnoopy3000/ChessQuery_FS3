import axios, { AxiosInstance } from 'axios';
import {
  AdminDashboard,
  CircuitBreakerState,
  createApiClient,
  EtlStatus,
  Game,
  Pagination,
  Pairing,
  Player,
  PlayerDashboard,
  PlayerStats,
  RatingChartPoint,
  RatingHistoryPoint,
  Round,
  Standing,
  Tournament,
  localStorageTokenStorage,
} from '@chessquery/shared';

const baseURL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8080';
export const storage = localStorageTokenStorage('chessquery.portal');

export const api: AxiosInstance = createApiClient({
  baseURL,
  storage,
  onAuthFailure: () => {
    if (window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
  },
});

type UnknownRecord = Record<string, unknown>;

interface EtlSyncResponse {
  id: number;
  source: string;
  status: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  recordsProcessed?: number;
  recordsFailed?: number;
  errorMessage?: string | null;
  cbState?: CircuitBreakerState | null;
}

const asRecord = (value: unknown): UnknownRecord =>
  value && typeof value === 'object' ? (value as UnknownRecord) : {};

const asString = (value: unknown): string | undefined => {
  if (typeof value === 'string' && value.trim().length > 0) return value;
  return undefined;
};

const asNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
};

const asArray = <T,>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);

const normalizeWinRate = (value: unknown) => {
  const winRate = asNumber(value);
  if (winRate == null) return 0;
  return winRate > 1 ? winRate / 100 : winRate;
};

const normalizePlayer = (value: unknown): Player => {
  const raw = asRecord(value);
  const country = asRecord(raw.country);
  const club = asRecord(raw.club);

  return {
    id: asNumber(raw.id ?? raw.playerId) ?? 0,
    firstName: asString(raw.firstName) ?? '',
    lastName: asString(raw.lastName) ?? '',
    rut: asString(raw.rut) ?? null,
    fideId: asString(raw.fideId) ?? null,
    lichessUsername: asString(raw.lichessUsername) ?? null,
    eloNational: asNumber(raw.eloNational) ?? null,
    eloFideStandard: asNumber(raw.eloFideStandard) ?? null,
    eloFideRapid: asNumber(raw.eloFideRapid) ?? null,
    eloFideBlitz: asNumber(raw.eloFideBlitz) ?? null,
    eloPlatform: asNumber(raw.eloPlatform) ?? null,
    fideTitle: asString(raw.fideTitle ?? raw.currentTitle) ?? null,
    ageCategory: asString(raw.ageCategory) ?? null,
    countryId: asNumber(country.id) ?? null,
    countryName: asString(country.name ?? raw.countryName ?? raw.countryIso ?? raw.region) ?? null,
    countryFlag: null,
    clubId: asNumber(club.id) ?? null,
    clubName: asString(club.name ?? raw.clubName) ?? null,
  };
};

const normalizeStats = (value: unknown): PlayerStats => {
  const raw = asRecord(value);

  return {
    playerId: asNumber(raw.playerId) ?? 0,
    totalGames: asNumber(raw.totalGames) ?? 0,
    wins: asNumber(raw.wins) ?? 0,
    losses: asNumber(raw.losses) ?? 0,
    draws: asNumber(raw.draws) ?? 0,
    winRate: normalizeWinRate(raw.winRate),
    favoriteOpening: asString(raw.favoriteOpening) ?? null,
    bestRating: asNumber(raw.bestRating ?? raw.bestElo) ?? null,
    currentStreak: asNumber(raw.currentStreak) ?? null,
  };
};

const normalizeGame = (value: unknown): Game => {
  const raw = asRecord(value);

  return {
    id: asNumber(raw.id) ?? 0,
    whitePlayerId: asNumber(raw.whitePlayerId) ?? 0,
    blackPlayerId: asNumber(raw.blackPlayerId) ?? 0,
    whiteName: asString(raw.whiteName),
    blackName: asString(raw.blackName),
    result: (asString(raw.result) ?? '—') as Game['result'],
    gameType: ((asString(raw.gameType) ?? 'TOURNAMENT') as Game['gameType']),
    openingId: asNumber(raw.openingId) ?? null,
    openingName: asString(raw.openingName ?? raw.openingEco) ?? null,
    playedAt: asString(raw.playedAt) ?? '',
  };
};

const normalizeRatingHistory = (value: unknown): RatingHistoryPoint => {
  const raw = asRecord(value);

  return {
    id: asNumber(raw.id) ?? 0,
    ratingType: asString(raw.ratingType) ?? '',
    value: asNumber(raw.value ?? raw.ratingValue) ?? 0,
    recordedAt: asString(raw.recordedAt) ?? '',
  };
};

const normalizeTournament = (value: unknown): Tournament => {
  const raw = asRecord(value);

  return {
    id: asNumber(raw.id) ?? 0,
    name: asString(raw.name) ?? 'Torneo',
    format: ((asString(raw.format) ?? 'SWISS') as Tournament['format']),
    status: ((asString(raw.status) ?? 'DRAFT') as Tournament['status']),
    startDate: asString(raw.startDate) ?? '',
    endDate: asString(raw.endDate) ?? null,
    location: asString(raw.location) ?? null,
    maxPlayers: asNumber(raw.maxPlayers) ?? 0,
    minElo: asNumber(raw.minElo) ?? null,
    maxElo: asNumber(raw.maxElo) ?? null,
    timeControl: asString(raw.timeControl) ?? null,
    rounds: asNumber(raw.rounds ?? raw.roundsTotal) ?? 0,
    organizerId: asNumber(raw.organizerId) ?? 0,
    registered: asNumber(raw.registered ?? raw.registeredCount) ?? 0,
  };
};

const normalizePagination = <T,>(
  value: unknown,
  itemNormalizer: (item: unknown) => T,
): Pagination<T> => {
  const raw = asRecord(value);
  const content = asArray(raw.content).map(itemNormalizer);

  return {
    content,
    page: asNumber(raw.page) ?? 0,
    size: asNumber(raw.size) ?? content.length,
    totalElements: asNumber(raw.totalElements) ?? content.length,
    totalPages: asNumber(raw.totalPages) ?? (content.length > 0 ? 1 : 0),
  };
};

const normalizeStanding = async (value: unknown): Promise<Standing> => {
  const raw = asRecord(value);
  const playerId = asNumber(raw.playerId) ?? 0;
  let playerName = asString(raw.playerName);

  if (!playerName && playerId) {
    try {
      const profile = await playerApi.publicProfile(playerId);
      playerName = `${profile.profile.firstName} ${profile.profile.lastName}`.trim();
    } catch {
      playerName = `Jugador #${playerId}`;
    }
  }

  return {
    rank: asNumber(raw.rank ?? raw.position) ?? 0,
    playerId,
    playerName: playerName || `Jugador #${playerId}`,
    points: asNumber(raw.points) ?? 0,
    buchholz: asNumber(raw.buchholz) ?? undefined,
    sonneborn: asNumber(raw.sonneborn ?? raw.sonnebornBerger) ?? undefined,
    gamesPlayed: asNumber(raw.gamesPlayed) ?? undefined,
  };
};

const normalizePairing = (value: unknown): Pairing => {
  const raw = asRecord(value);

  return {
    id: asNumber(raw.id) ?? 0,
    roundId: asNumber(raw.roundId) ?? 0,
    boardNumber: asNumber(raw.boardNumber) ?? 0,
    whitePlayerId: asNumber(raw.whitePlayerId) ?? null,
    blackPlayerId: asNumber(raw.blackPlayerId) ?? null,
    whitePlayerName: asString(raw.whitePlayerName),
    whitePlayerRating: asNumber(raw.whitePlayerRating),
    blackPlayerName: asString(raw.blackPlayerName),
    blackPlayerRating: asNumber(raw.blackPlayerRating),
    result: ((asString(raw.result) ?? null) as Pairing['result']),
  };
};

const normalizeRound = (value: unknown): Round => {
  const raw = asRecord(value);

  return {
    id: asNumber(raw.id) ?? 0,
    tournamentId: asNumber(raw.tournamentId) ?? 0,
    roundNumber: asNumber(raw.roundNumber) ?? 0,
    status: ((asString(raw.status) ?? 'PENDING') as Round['status']),
    pairings: asArray(raw.pairings).map(normalizePairing),
  };
};

export const playerApi = {
  dashboard: (): Promise<PlayerDashboard> =>
    api.get('/api/player/me/dashboard').then((r) => {
      const raw = asRecord(r.data);
      return {
        profile: normalizePlayer(raw.profile),
        recentGames: asArray(raw.recentGames).map(normalizeGame),
        stats: normalizeStats(raw.stats),
      };
    }),

  ratingChart: (type: string, months: number): Promise<RatingChartPoint[]> =>
    api
      .get('/api/player/me/rating-chart', { params: { type, months } })
      .then((r) =>
        asArray<unknown>(r.data).map((point) => {
          const raw = asRecord(point);
          return {
            date: asString(raw.date) ?? '',
            rating: asNumber(raw.rating) ?? 0,
          };
        }),
      ),

  publicProfile: (id: string | number): Promise<PlayerDashboard> =>
    api.get(`/api/player/${id}/profile`).then((r) => {
      const raw = asRecord(r.data);
      return {
        profile: normalizePlayer(raw.profile),
        recentGames: asArray(raw.recentGames).map(normalizeGame),
        stats: normalizeStats(raw.stats),
      };
    }),

  ratingHistory: (id: string | number, type?: string): Promise<RatingHistoryPoint[]> =>
    api
      .get(`/api/player/${id}/rating-history`, { params: { type } })
      .then((r) => asArray<unknown>(r.data).map(normalizeRatingHistory))
      .catch(() => []),

  search: (q: string): Promise<Player[]> =>
    api.get('/api/player/search', { params: { q } }).then((r) => asArray<unknown>(r.data).map(normalizePlayer)),

  rankings: (category?: string, region?: string): Promise<Player[]> =>
    api
      .get('/api/player/rankings', { params: { category, region } })
      .then((r) => asArray<unknown>(r.data).map(normalizePlayer)),
};

export const tournamentApi = {
  list: (params?: Record<string, string | number | undefined>): Promise<Pagination<Tournament>> =>
    api.get('/api/organizer/tournaments', { params }).then((r) => normalizePagination(r.data, normalizeTournament)),

  detail: (id: string | number): Promise<Tournament> =>
    api.get(`/api/organizer/tournaments/${id}`).then((r) => normalizeTournament(r.data)),

  round: (id: string | number, roundNumber: number): Promise<Round> =>
    api.get(`/api/organizer/tournaments/${id}/round/${roundNumber}`).then((r) => normalizeRound(r.data)),

  standings: async (id: string | number): Promise<Standing[]> => {
    const response = await api.get(`/api/organizer/tournaments/${id}/standings`);
    return Promise.all(asArray<unknown>(response.data).map(normalizeStanding));
  },

  join: (id: string | number, payload?: Record<string, unknown>) =>
    api.post(`/api/organizer/tournaments/${id}/join`, payload ?? {}).then((r) => r.data),
};

export const organizerApi = {
  listTournaments: (params?: Record<string, string | number | undefined>) => tournamentApi.list(params),
  tournamentDetail: (id: string | number) => tournamentApi.detail(id),
  tournamentStandings: (id: string | number) => tournamentApi.standings(id),
  round: (id: string | number, roundNumber: number) => tournamentApi.round(id, roundNumber),
  generateRound: (id: string | number, roundNumber: number) =>
    api.post(`/api/organizer/tournaments/${id}/rounds/${roundNumber}/generate`, {}).then((r) => normalizeRound(r.data)),
  patchPairingResult: (pairingId: string | number, result: string) =>
    api.patch(`/api/organizer/pairings/${pairingId}/result`, { result }).then((r) => normalizePairing(r.data)),
};

export const adminApi = {
  dashboard: (): Promise<AdminDashboard> => api.get('/api/admin/dashboard').then((r) => r.data),
  etlStatus: (): Promise<EtlStatus> => api.get('/api/admin/etl/status').then((r) => r.data),
  sync: (source: string): Promise<EtlSyncResponse> =>
    api.post(`/api/admin/etl/sync/${source}`, {}).then((r) => r.data),
  searchUsers: (q: string): Promise<Player[]> =>
    api.get('/api/admin/users', { params: { q } }).then((r) => asArray<unknown>(r.data).map(normalizePlayer)),
};

export const gameApi = {
  recent: (playerId: number | string, size = 10): Promise<Pagination<Game>> =>
    api
      .get(`/api/player/${playerId}/profile`)
      .then((r) => {
        const raw = asRecord(r.data);
        const games = asArray(raw.recentGames).map(normalizeGame).slice(0, size);
        return {
          content: games,
          page: 0,
          size,
          totalElements: games.length,
          totalPages: games.length > 0 ? 1 : 0,
        };
      }),
};

export const authApi = {
  login: (email: string, password: string) =>
    axios.post(`${baseURL}/auth/login`, { email, password }).then((r) => r.data),
  register: (input: { email: string; password: string; firstName: string; lastName: string; role?: string }) =>
    axios.post(`${baseURL}/auth/register`, input).then((r) => r.data),
};
