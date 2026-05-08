import { BadRequestException, Injectable } from '@nestjs/common';
import { UpstreamHttpService } from '../common/http.service';
import {
  DashboardResponse,
  RatingChartPoint,
  RatingHistoryEntry,
} from './dto/dashboard.dto';

@Injectable()
export class PlayerService {
  constructor(private readonly http: UpstreamHttpService) {}

  /**
   * Resuelve el supabaseUserId (UUID) al player.id numérico consultando
   * ms-users. Cachea por proceso (no por request) ya que el mapping es estable.
   */
  private readonly playerIdCache = new Map<string, number>();
  private async resolvePlayerId(supabaseUserId: string): Promise<number> {
    // Si ya viene numérico (legacy / tests), úsalo tal cual
    if (/^\d+$/.test(supabaseUserId)) return Number(supabaseUserId);
    const cached = this.playerIdCache.get(supabaseUserId);
    if (cached) return cached;
    const { msUsers } = this.http.urls;
    const player = await this.http.get<{ id: number }>(
      `${msUsers}/users/by-supabase-id/${supabaseUserId}`,
    );
    if (!player?.id) {
      throw new BadRequestException(
        `Player no encontrado para supabaseUserId=${supabaseUserId}`,
      );
    }
    this.playerIdCache.set(supabaseUserId, player.id);
    return player.id;
  }

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
    const playerId = await this.resolvePlayerId(userId);

    const [profile, recentGamesPage, stats] = await Promise.all([
      this.http.get<unknown>(`${msUsers}/users/${playerId}/profile`),
      this.http
        .get<{ content: unknown[] }>(`${msGame}/games?playerId=${playerId}&size=5`)
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
    const playerId = await this.resolvePlayerId(userId);
    const history = await this.http.get<RatingHistoryEntry[]>(
      `${msUsers}/users/${playerId}/rating-history?type=${encodeURIComponent(ratingType)}`,
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

  /**
   * Selecciona aleatoriamente un rival para el jugador. Estrategia:
   * 1. Trae el ranking nacional (toda la base federada + registrada).
   * 2. Filtra al propio jugador.
   * 3. Filtra a quienes tengan rating muy distante (±400 pts respecto del
   *    primario) cuando hay rating; si el solicitante no tiene rating,
   *    no filtra por gap.
   * 4. Devuelve uno al azar con su perfil completo + colores asignados
   *    (el jugador autenticado siempre va de blancas).
   */
  async findRandomOpponent(userId: string): Promise<{
    you: unknown;
    opponent: unknown;
    yourColor: 'white' | 'black';
    opponentColor: 'white' | 'black';
  }> {
    const { msUsers } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);

    const meProfile = await this.http
      .get<{ id?: number; eloNational?: number | null; eloFideStandard?: number | null }>(
        `${msUsers}/users/${playerId}/profile`,
      )
      .catch(() => ({} as { id?: number; eloNational?: number | null; eloFideStandard?: number | null }));

    const myRating = meProfile.eloFideStandard ?? meProfile.eloNational ?? null;

    const ranking = await this.http.get<Array<{
      playerId: number;
      eloNational?: number | null;
      eloFideStandard?: number | null;
    }>>(`${msUsers}/users/ranking?page=0&size=200`);

    const candidates = (Array.isArray(ranking) ? ranking : [])
      .filter((p) => p.playerId !== playerId);

    const closeEnough = myRating == null
      ? candidates
      : candidates.filter((p) => {
          const r = p.eloFideStandard ?? p.eloNational;
          return r == null || Math.abs(r - myRating) <= 400;
        });

    const pool = closeEnough.length > 0 ? closeEnough : candidates;
    if (pool.length === 0) {
      throw new BadRequestException('No hay rivales disponibles en este momento');
    }

    const opponent = pool[Math.floor(Math.random() * pool.length)];
    const opponentProfile = await this.http
      .get<unknown>(`${msUsers}/users/${opponent.playerId}/profile`)
      .catch(() => ({ id: opponent.playerId }));

    return {
      you: meProfile,
      opponent: opponentProfile,
      yourColor: 'white',
      opponentColor: 'black',
    };
  }

  /**
   * Persiste una partida casual jugada en ChessQuery. El frontend se
   * limita a reportar el resultado (1-0, 0-1 o 1/2-1/2); ms-game
   * recalcula ELO y publica game.finished + 2 elo.updated.
   */
  async submitCasualGame(userId: string, body: Record<string, unknown>): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    const payload = {
      ...body,
      gameType: 'CASUAL',
      whitePlayerId: body.whitePlayerId ?? playerId,
    };
    return this.http.post<unknown>(`${msGame}/games`, payload);
  }

  // ── Live games (proxy a ms-game) ─────────────────────────────────────

  async createLiveGame(userId: string, body: Record<string, unknown>): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live`, {
      whitePlayerId: playerId,
      whiteEloBefore: body.whiteEloBefore,
      timeControlInitialMs: body.timeControlInitialMs,
      timeControlIncrementMs: body.timeControlIncrementMs,
    });
  }

  async getLiveGame(id: string): Promise<unknown> {
    const { msGame } = this.http.urls;
    return this.http.get<unknown>(`${msGame}/games/live/${id}`);
  }

  async joinLiveGame(userId: string, id: string, body: Record<string, unknown>): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/join`, {
      playerId,
      eloBefore: body.eloBefore,
    });
  }

  async moveLiveGame(userId: string, id: string, body: Record<string, unknown>): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/move`, {
      playerId,
      uci: body.uci,
      clockWhiteMs: body.clockWhiteMs,
      clockBlackMs: body.clockBlackMs,
    });
  }

  async resignLiveGame(userId: string, id: string): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/resign`, {
      playerId,
    });
  }

  async drawLiveGame(userId: string, id: string): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/draw`, {
      playerId,
    });
  }

  async timeoutLiveGame(userId: string, id: string): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/timeout`, {
      playerId,
    });
  }

  async rematchLiveGame(userId: string, id: string): Promise<unknown> {
    const { msGame } = this.http.urls;
    const playerId = await this.resolvePlayerId(userId);
    return this.http.post<unknown>(`${msGame}/games/live/${id}/rematch`, {
      playerId,
    });
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
