import { Injectable } from '@nestjs/common';
import { UpstreamHttpService } from '../common/http.service';
import {
  EnrichedPairing,
  EnrichedRoundResponse,
  PlayerProfile,
  RoundResponse,
} from './dto/pairing.dto';

@Injectable()
export class OrganizerService {
  constructor(private readonly http: UpstreamHttpService) {}

  async listTournaments(organizerId: string, query: Record<string, string | undefined>) {
    const { msTournament } = this.http.urls;
    const params = new URLSearchParams();
    params.set('organizerId', organizerId);
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null) params.set(k, v);
    }
    const response = await this.http.get<{
      content?: Array<Record<string, unknown>>;
      page?: number;
      size?: number;
      totalElements?: number;
      totalPages?: number;
    }>(`${msTournament}/tournaments?${params.toString()}`);

    if (!Array.isArray(response?.content)) {
      return response;
    }

    const filtered = response.content.filter(
      (item) => Number(item.organizerId) === Number(organizerId),
    );

    return {
      ...response,
      content: filtered,
      page: 0,
      size: filtered.length || response.size || 0,
      totalElements: filtered.length,
      totalPages: filtered.length > 0 ? 1 : 0,
    };
  }

  async getTournament(tournamentId: string) {
    const { msTournament } = this.http.urls;
    return this.http.get<unknown>(`${msTournament}/tournaments/${tournamentId}`);
  }

  async createTournament(organizerId: string, body: Record<string, unknown>) {
    const { msTournament } = this.http.urls;
    const payload = { ...body, organizerId: Number(organizerId) };
    return this.http.post<unknown>(`${msTournament}/tournaments`, payload, {
      headers: { 'X-User-Role': 'ORGANIZER', 'X-User-Id': organizerId },
    });
  }

  async getEnrichedRound(tournamentId: string, roundNumber: string): Promise<EnrichedRoundResponse> {
    const { msTournament, msUsers } = this.http.urls;

    const round = await this.http.get<RoundResponse>(
      `${msTournament}/tournaments/${tournamentId}/rounds/${roundNumber}`,
    );

    const playerIds = new Set<number>();
    for (const p of round.pairings) {
      if (p.whitePlayerId != null) playerIds.add(p.whitePlayerId);
      if (p.blackPlayerId != null) playerIds.add(p.blackPlayerId);
    }

    const profiles = await Promise.all(
      Array.from(playerIds).map((id) =>
        this.http
          .get<PlayerProfile>(`${msUsers}/users/${id}/profile`)
          .catch(() => ({ id } as PlayerProfile)),
      ),
    );
    const byId = new Map<number, PlayerProfile>();
    for (const p of profiles) byId.set(p.id, p);

    const enriched: EnrichedPairing[] = round.pairings.map((p) => {
      const w = p.whitePlayerId != null ? byId.get(p.whitePlayerId) : undefined;
      const b = p.blackPlayerId != null ? byId.get(p.blackPlayerId) : undefined;
      return {
        ...p,
        whitePlayerName: w ? `${w.firstName ?? ''} ${w.lastName ?? ''}`.trim() || undefined : undefined,
        whitePlayerRating: w?.eloFideStandard ?? w?.eloNational,
        blackPlayerName: b ? `${b.firstName ?? ''} ${b.lastName ?? ''}`.trim() || undefined : undefined,
        blackPlayerRating: b?.eloFideStandard ?? b?.eloNational,
      };
    });

    return { ...round, pairings: enriched };
  }

  async join(tournamentId: string, userId: string, body: Record<string, unknown>) {
    const { msTournament } = this.http.urls;
    const payload = { ...body, playerId: body.playerId ?? Number(userId) };
    return this.http.post<unknown>(
      `${msTournament}/tournaments/${tournamentId}/registrations`,
      payload,
    );
  }

  async generateRound(tournamentId: string, roundNumber: string, userId: string) {
    const { msTournament } = this.http.urls;
    return this.http.post<unknown>(
      `${msTournament}/tournaments/${tournamentId}/rounds/${roundNumber}`,
      {},
      {
        headers: { 'X-User-Role': 'ORGANIZER', 'X-User-Id': userId },
      },
    );
  }

  async patchPairingResult(pairingId: string, body: Record<string, unknown>) {
    const { msTournament } = this.http.urls;
    return this.http.patch<unknown>(
      `${msTournament}/tournaments/pairings/${pairingId}/result`,
      body,
    );
  }

  async getStandings(tournamentId: string) {
    const { msTournament } = this.http.urls;
    return this.http.get<unknown>(`${msTournament}/tournaments/${tournamentId}/standings`);
  }
}
