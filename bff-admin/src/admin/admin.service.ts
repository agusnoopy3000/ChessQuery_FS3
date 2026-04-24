import { Injectable } from '@nestjs/common';
import { UpstreamHttpService } from '../common/http.service';
import { AdminDashboardResponse } from './dto/dashboard.dto';

@Injectable()
export class AdminService {
  constructor(private readonly http: UpstreamHttpService) {}

  async getDashboard(): Promise<AdminDashboardResponse> {
    const { msUsers, msTournament, msGame, msAnalytics, msEtl } = this.http.urls;

    const settle = <T>(p: Promise<T>) =>
      p.then(
        (v) => ({ ok: true as const, value: v }),
        (e: unknown) => ({ ok: false as const, error: (e as Error).message ?? String(e) }),
      );

    const [users, tournaments, games, analytics, etl] = await Promise.all([
      settle(this.http.get<{ totalElements?: number; total?: number }>(
        `${msUsers}/users?size=1`,
      )),
      settle(this.http.get<unknown>(`${msTournament}/tournaments?status=IN_PROGRESS&size=20`)),
      settle(this.http.get<unknown>(`${msGame}/games?size=10`)),
      settle(this.http.get<unknown>(`${msAnalytics}/analytics/platform/summary`)),
      settle(this.http.get<unknown>(`${msEtl}/etl/status`)),
    ]);

    return {
      users: users.ok
        ? { total: users.value.totalElements ?? users.value.total ?? null }
        : { total: null, error: users.error },
      tournaments: tournaments.ok
        ? { active: tournaments.value }
        : { active: null, error: tournaments.error },
      games: games.ok ? { recent: games.value } : { recent: null, error: games.error },
      analytics: analytics.ok
        ? { platform: analytics.value }
        : { platform: null, error: analytics.error },
      etl: etl.ok ? { status: etl.value } : { status: null, error: etl.error },
    };
  }

  async getEtlStatus() {
    const { msEtl } = this.http.urls;
    return this.http.get<unknown>(`${msEtl}/etl/status`);
  }

  async triggerEtlSync(source: string) {
    const { msEtl } = this.http.urls;
    return this.http.post<unknown>(`${msEtl}/etl/sync/${encodeURIComponent(source)}`, {});
  }

  async searchUsers(q: string) {
    const { msUsers } = this.http.urls;
    return this.http.get<unknown>(`${msUsers}/users/search?q=${encodeURIComponent(q)}`);
  }
}
