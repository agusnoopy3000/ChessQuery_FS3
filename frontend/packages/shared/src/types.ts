// ── Auth ─────────────────────────────────────────────────────────
export type Role = 'PLAYER' | 'ORGANIZER' | 'ADMIN';

export interface AuthUser {
  id: number;
  email: string;
  role: Role;
  name?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: number;
  email: string;
  role: Role;
}

// ── Shared contracts ─────────────────────────────────────────────
export interface Pagination<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  timestamp: string;
}

// ── Domain ───────────────────────────────────────────────────────
export interface Player {
  id: number;
  firstName: string;
  lastName: string;
  rut?: string | null;
  fideId?: string | null;
  lichessUsername?: string | null;
  eloNational?: number | null;
  eloFideStandard?: number | null;
  eloFideRapid?: number | null;
  eloFideBlitz?: number | null;
  eloPlatform?: number | null;
  fideTitle?: string | null;
  ageCategory?: string | null;
  countryId?: number | null;
  countryName?: string | null;
  countryFlag?: string | null;
  clubId?: number | null;
  clubName?: string | null;
}

export interface RatingHistoryPoint {
  id: number;
  ratingType: string;
  value: number;
  recordedAt: string;
}

export interface Game {
  id: number;
  whitePlayerId: number;
  blackPlayerId: number;
  whiteName?: string;
  blackName?: string;
  result: '1-0' | '0-1' | '1/2-1/2' | string;
  gameType: 'TOURNAMENT' | 'CASUAL';
  openingId?: number | null;
  openingName?: string | null;
  playedAt: string;
}

export interface PlayerStats {
  playerId: number;
  totalGames: number;
  wins: number;
  losses: number;
  draws: number;
  winRate: number;
  favoriteOpening?: string | null;
  bestRating?: number | null;
  currentStreak?: number | null;
}

export interface Tournament {
  id: number;
  name: string;
  format: 'SWISS' | 'ROUND_ROBIN' | 'KNOCKOUT';
  status: 'DRAFT' | 'OPEN' | 'IN_PROGRESS' | 'FINISHED';
  startDate: string;
  endDate?: string | null;
  location?: string | null;
  maxPlayers: number;
  minElo?: number | null;
  maxElo?: number | null;
  timeControl?: string | null;
  rounds: number;
  organizerId: number;
  registered?: number;
}

export interface TournamentRegistration {
  id: number;
  tournamentId: number;
  playerId: number;
  playerName?: string;
  seedRating: number;
  status: 'CONFIRMED' | 'WAITLISTED' | 'CANCELLED';
  registeredAt: string;
}

export interface Pairing {
  id: number;
  roundId: number;
  boardNumber: number;
  whitePlayerId: number | null;
  blackPlayerId: number | null;
  whitePlayerName?: string;
  whitePlayerRating?: number;
  blackPlayerName?: string;
  blackPlayerRating?: number;
  result: '1-0' | '0-1' | '1/2-1/2' | null;
}

export interface Round {
  id: number;
  tournamentId: number;
  roundNumber: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'FINISHED';
  pairings: Pairing[];
}

export interface Standing {
  rank: number;
  playerId: number;
  playerName: string;
  points: number;
  buchholz?: number;
  sonneborn?: number;
  gamesPlayed?: number;
}

// ── Dashboard ────────────────────────────────────────────────────
export interface PlayerDashboard {
  profile: Player;
  recentGames: Game[];
  stats: PlayerStats;
}

export interface RatingChartPoint {
  date: string;
  rating: number;
}

// ── Admin ────────────────────────────────────────────────────────
export type CircuitBreakerState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export interface EtlStatus {
  status: string;
  circuitBreakers: Record<
    string,
    { state: CircuitBreakerState; failureCount: number; failureThreshold: number }
  >;
}

export interface EtlLogEntry {
  id: number;
  source: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED';
  startedAt: string;
  finishedAt?: string | null;
  recordsProcessed: number;
  recordsFailed: number;
  cbState?: CircuitBreakerState | null;
  errorMessage?: string | null;
}

export interface AdminDashboard {
  users: { total: number | null; error?: string };
  tournaments: { active: unknown | null; error?: string };
  games: { recent: unknown | null; error?: string };
  analytics: { platform: unknown | null; error?: string };
  etl: { status: unknown | null; error?: string };
}
