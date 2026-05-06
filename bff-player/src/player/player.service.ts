import { HttpException, Injectable } from '@nestjs/common';
import { UpstreamHttpService } from '../common/http.service';
import {
  DashboardResponse,
  RatingChartPoint,
  RatingHistoryEntry,
} from './dto/dashboard.dto';

@Injectable()
export class PlayerService {
  constructor(private readonly http: UpstreamHttpService) {}

  async getDashboard(userId: string): Promise<DashboardResponse> {
    const { msUsers, msGame, msAnalytics } = this.http.urls;

    const [profile, recentGamesPage, stats] = await Promise.all([
      this.fetchOwnProfileOrPlaceholder(userId, `${msUsers}/users/${userId}/profile`),
      this.http.get<{ content: unknown[] }>(
        `${msGame}/games?playerId=${userId}&size=5`,
      ),
      this.fetchStatsOrDefault(`${msAnalytics}/analytics/players/${userId}/stats`, userId),
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
      this.http.get<{ content: unknown[] }>(
        `${msGame}/games?playerId=${playerId}&size=10`,
      ),
      this.fetchStatsOrDefault(`${msAnalytics}/analytics/players/${playerId}/stats`, playerId),
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

  private async fetchProfileWithRetry(url: string): Promise<unknown> {
    let attempts = 0;
    let lastError: unknown;

    while (attempts < 3) {
      try {
        return await this.http.get<unknown>(url);
      } catch (error) {
        lastError = error;
        if (!this.isNotFound(error) || attempts === 2) {
          throw error;
        }
        await this.sleep(250);
      }
      attempts += 1;
    }

    throw lastError;
  }

  private async fetchOwnProfileOrPlaceholder(userId: string, url: string): Promise<unknown> {
    try {
      return await this.fetchProfileWithRetry(url);
    } catch (error) {
      if (this.isNotFound(error)) {
        return {
          id: Number(userId),
          firstName: 'Jugador',
          lastName: `#${userId}`,
          email: null,
          rut: null,
          birthDate: null,
          gender: null,
          region: null,
          fideId: null,
          lichessUsername: null,
          country: null,
          club: null,
          eloNational: null,
          eloFideStandard: null,
          eloFideRapid: null,
          eloFideBlitz: null,
          eloPlatform: null,
          currentTitle: null,
          createdAt: null,
          updatedAt: null,
        };
      }
      throw error;
    }
  }

  private async fetchStatsOrDefault(url: string, userId: string): Promise<unknown> {
    try {
      return await this.http.get<unknown>(url);
    } catch (error) {
      if (this.isNotFound(error)) {
        return {
          playerId: Number(userId),
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
      throw error;
    }
  }

  private isNotFound(error: unknown): boolean {
    return error instanceof HttpException && error.getStatus() === 404;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
