import { Injectable } from '@nestjs/common';
import { UpstreamHttpService } from '../common/http.service';
import {
  DashboardResponse,
  RatingChartPoint,
  RatingHistoryEntry,
} from './dto/dashboard.dto';

@Injectable()
export class PlayerService {
  constructor(private readonly http: UpstreamHttpService) {}

  private emptyStats(playerId: string | number) {
    return {
      playerId: Number(playerId) || 0,
      totalGames: 0,
      wins: 0,
      losses: 0,
      draws: 0,
      winRate: 0,
      avgMoves: 0,
      currentStreak: 0,
      bestElo: null,
      lastRefreshed: null,
    };
  }

  async getDashboard(userId: string): Promise<DashboardResponse> {
    const { msUsers, msGame, msAnalytics } = this.http.urls;

    const [profile, recentGamesPage, stats] = await Promise.all([
      this.http.get<unknown>(`${msUsers}/users/${userId}/profile`),
      this.http
        .get<{ content: unknown[] }>(`${msGame}/games?playerId=${userId}&size=5`)
        .catch(() => ({ content: [] as unknown[] })),
      this.http
        .get<unknown>(`${msAnalytics}/analytics/players/${userId}/stats`)
        .catch(() => this.emptyStats(userId)),
    ]);

    return {
      profile,
      recentGames: recentGamesPage?.content ?? [],
      stats,
    };
  }

  async getPublicProfile(playerId: string): Promise<DashboardResponse> {
    const { msUsers, msGame, msAnalytics } = this.http.urls;

    const [profile, recentGamesPage, stats] = await Promise.all([
      this.http.get<unknown>(`${msUsers}/users/${playerId}/profile`),
      this.http
        .get<{ content: unknown[] }>(`${msGame}/games?playerId=${playerId}&size=10`)
        .catch(() => ({ content: [] as unknown[] })),
      this.http
        .get<unknown>(`${msAnalytics}/analytics/players/${playerId}/stats`)
        .catch(() => this.emptyStats(playerId)),
    ]);

    return {
      profile,
      recentGames: recentGamesPage?.content ?? [],
      stats,
    };
  }

  async getRatingChart(
    userId: string,
    ratingType: string,
    months: number,
  ): Promise<RatingChartPoint[]> {
    const { msUsers } = this.http.urls;
    const history = await this.http.get<RatingHistoryEntry[]>(
      `${msUsers}/users/${userId}/rating-history?type=${encodeURIComponent(ratingType)}`,
    );

    const cutoff = new Date();
    cutoff.setMonth(cutoff.getMonth() - months);

    return history
      .filter((h) => new Date(h.recordedAt) >= cutoff)
      .map((h) => ({
        date: h.recordedAt.substring(0, 10),
        rating: h.value,
      }));
  }

  async searchPlayers(query: string): Promise<unknown> {
    const { msUsers } = this.http.urls;
    return this.http.get<unknown>(
      `${msUsers}/users/search?q=${encodeURIComponent(query)}`,
    );
  }

  async getRankings(category?: string, region?: string): Promise<unknown> {
    const { msUsers } = this.http.urls;
    const params = new URLSearchParams();
    if (category) params.set('category', category);
    if (region) params.set('region', region);
    const qs = params.toString() ? `?${params.toString()}` : '';
    return this.http.get<unknown>(`${msUsers}/users/ranking${qs}`);
  }

  async syncFromAuth(body: {
    id: number;
    email?: string;
    firstName?: string;
    lastName?: string;
    lichessUsername?: string;
  }): Promise<unknown> {
    const { msUsers } = this.http.urls;
    return this.http.post<unknown>(`${msUsers}/users/sync`, body);
  }

  async getLichessProfile(playerId: string): Promise<{
    username: string | null;
    user: unknown | null;
    games: unknown[];
    error?: string;
  }> {
    const { msUsers, msEtl } = this.http.urls;
    const profile = await this.http.get<{ lichessUsername?: string | null }>(
      `${msUsers}/users/${playerId}/profile`,
    );
    const username = profile?.lichessUsername?.trim() || null;

    if (!username) {
      return { username: null, user: null, games: [], error: 'Jugador sin lichessUsername registrado.' };
    }

    try {
      const [user, games] = await Promise.all([
        this.http.get<unknown>(`${msEtl}/lichess/users/${encodeURIComponent(username)}`),
        this.http.get<unknown[]>(`${msEtl}/lichess/users/${encodeURIComponent(username)}/games?max=10`),
      ]);
      return { username, user, games: games ?? [] };
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'No se pudo consultar Lichess';
      return { username, user: null, games: [], error: msg };
    }
  }
}
