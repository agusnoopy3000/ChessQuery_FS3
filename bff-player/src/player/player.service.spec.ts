import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException, ServiceUnavailableException } from '@nestjs/common';
import { PlayerService } from './player.service';
import { UpstreamHttpService } from '../common/http.service';

describe('PlayerService', () => {
  let service: PlayerService;
  let http: {
    get: jest.Mock;
    post: jest.Mock;
    patch: jest.Mock;
    urls: Record<string, string>;
  };

  beforeEach(async () => {
    http = {
      get: jest.fn(),
      post: jest.fn(),
      patch: jest.fn(),
      urls: {
        msUsers: 'http://ms-users:8081',
        msTournament: 'http://ms-tournament:8082',
        msGame: 'http://ms-game:8083',
        msAnalytics: 'http://ms-analytics:8084',
        msNotifications: 'http://ms-notifications:8085',
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PlayerService,
        { provide: UpstreamHttpService, useValue: http },
      ],
    }).compile();

    service = module.get<PlayerService>(PlayerService);
  });

  // ── getDashboard ─────────────────────────────────────────────────────────

  describe('getDashboard', () => {
    it('arma el DTO con profile, recentGames y stats', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile')) return { id: 5, firstName: 'Ana', lastName: 'P' };
        if (url.includes('/games')) return { content: [{ whitePlayerId: 5, blackPlayerId: 7 }] };
        if (url.includes('/analytics/players/')) return { playerId: 5, totalGames: 0 };
        return {};
      });
      const result = await service.getDashboard('5');
      expect(result.profile).toMatchObject({ id: 5 });
      expect(Array.isArray(result.recentGames)).toBe(true);
      expect(result.stats).toBeDefined();
    });

    it('propaga 503 si ms-users profile cae', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile') && !url.includes('analytics'))
          throw new ServiceUnavailableException('down');
        if (url.includes('/games')) return { content: [] };
        if (url.includes('/analytics/players/')) return { playerId: 5 };
        return {};
      });
      await expect(service.getDashboard('5')).rejects.toBeInstanceOf(ServiceUnavailableException);
    });

    it('cuando ms-game cae, recentGames queda vacío', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile')) return { id: 5 };
        if (url.includes('/games')) throw new Error('game down');
        if (url.includes('/analytics/players/')) return { playerId: 5 };
        return {};
      });
      const r = await service.getDashboard('5');
      expect(r.recentGames).toEqual([]);
    });

    it('cuando ms-analytics cae, stats devuelve emptyStats', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile')) return { id: 5 };
        if (url.includes('/games')) return { content: [] };
        if (url.includes('/analytics/players/')) throw new Error('an down');
        return {};
      });
      const r = await service.getDashboard('5');
      expect((r.stats as Record<string, unknown>).totalGames).toBe(0);
    });
  });

  // ── resolvePlayerId / cache ───────────────────────────────────────────────

  describe('resolvePlayerId (vía métodos públicos)', () => {
    it('UUID resuelve vía ms-users by-supabase-id y cachea', async () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000';
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('by-supabase-id')) return { id: 42 };
        if (url.includes('/profile')) return { id: 42 };
        if (url.includes('/games')) return { content: [] };
        return { playerId: 42 };
      });
      await service.getDashboard(uuid);
      await service.getDashboard(uuid); // segundo call usa cache
      const supabaseLookups = http.get.mock.calls.filter((c) =>
        String(c[0]).includes('by-supabase-id'),
      );
      expect(supabaseLookups.length).toBe(1);
    });

    it('UUID sin match en ms-users lanza BadRequestException', async () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440001';
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('by-supabase-id')) return null;
        return {};
      });
      await expect(service.getDashboard(uuid)).rejects.toBeInstanceOf(BadRequestException);
    });
  });

  // ── getPublicProfile / getRatingChart / search / ranking / sync ──────────

  describe('endpoints simples', () => {
    it('getPublicProfile arma el DTO sin resolvePlayerId', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile')) return { id: 9 };
        if (url.includes('/games')) return { content: [] };
        return { playerId: 9 };
      });
      const r = await service.getPublicProfile('9');
      expect(r.profile).toMatchObject({ id: 9 });
    });

    it('getRatingChart filtra por meses y mapea date/rating', async () => {
      const recent = new Date();
      const old = new Date();
      old.setMonth(old.getMonth() - 12);
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('rating-history')) {
          return [
            { recordedAt: recent.toISOString(), value: 1500 },
            { recordedAt: old.toISOString(), value: 1400 },
          ];
        }
        return {};
      });
      const r = await service.getRatingChart('5', 'NATIONAL', 6);
      expect(r).toHaveLength(1);
      expect(r[0].rating).toBe(1500);
    });

    it('searchPlayers delega a ms-users con el query escapado', async () => {
      http.get.mockResolvedValue([{ id: 1 }]);
      await service.searchPlayers('Juan & Pérez');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('q=Juan%20%26%20P%C3%A9rez'));
    });

    it('getRankings sin filtros llama sin querystring', async () => {
      http.get.mockResolvedValue([]);
      await service.getRankings();
      expect(http.get).toHaveBeenCalledWith('http://ms-users:8081/users/ranking');
    });

    it('getRankings con category y region pasa params', async () => {
      http.get.mockResolvedValue([]);
      await service.getRankings('rapid', 'CL');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('category=rapid'));
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('region=CL'));
    });

    it('syncFromAuth POSTea a /users/sync', async () => {
      http.post.mockResolvedValue({ id: 1 });
      await service.syncFromAuth({ id: 1, email: 'a@b.cl' });
      expect(http.post).toHaveBeenCalledWith(
        'http://ms-users:8081/users/sync',
        { id: 1, email: 'a@b.cl' },
      );
    });
  });

  // ── findRandomOpponent ──────────────────────────────────────────────────

  describe('findRandomOpponent', () => {
    it('lanza 400 cuando no hay rivales', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/profile')) return { id: 5, eloNational: 1500 };
        if (url.includes('/ranking')) return [];
        return {};
      });
      await expect(service.findRandomOpponent('5')).rejects.toBeInstanceOf(BadRequestException);
    });

    it('filtra por distancia ±400 cuando hay rating propio', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.endsWith('/users/5/profile')) return { id: 5, eloNational: 1500 };
        if (url.includes('/ranking')) {
          return [
            { playerId: 5, eloNational: 1500 },
            { playerId: 6, eloNational: 1600 },
            { playerId: 7, eloNational: 3000 }, // muy lejos
          ];
        }
        if (url.endsWith('/profile')) return { id: 6 };
        return {};
      });
      const r = await service.findRandomOpponent('5');
      expect((r.opponent as { id: number }).id).toBe(6);
      expect(r.yourColor).toBe('white');
    });

    it('sin rating propio no filtra por gap', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.endsWith('/users/5/profile')) return { id: 5 };
        if (url.includes('/ranking')) return [{ playerId: 6, eloNational: 3000 }];
        if (url.endsWith('/profile')) return { id: 6 };
        return {};
      });
      const r = await service.findRandomOpponent('5');
      expect((r.opponent as { id: number }).id).toBe(6);
    });
  });

  // ── Live games proxy ────────────────────────────────────────────────────

  describe('live games proxy', () => {
    it('createLiveGame manda whitePlayerId del usuario', async () => {
      http.post.mockResolvedValue({ id: 1 });
      await service.createLiveGame('5', { whiteEloBefore: 1500, timeControlInitialMs: 600000 });
      expect(http.post).toHaveBeenCalledWith(
        'http://ms-game:8083/games/live',
        expect.objectContaining({ whitePlayerId: 5 }),
      );
    });

    it('getLiveGame delega', async () => {
      http.get.mockResolvedValue({ id: 1 });
      await service.getLiveGame('1');
      expect(http.get).toHaveBeenCalledWith('http://ms-game:8083/games/live/1');
    });

    it('joinLiveGame manda playerId y eloBefore', async () => {
      http.post.mockResolvedValue({ id: 1 });
      await service.joinLiveGame('5', '1', { eloBefore: 1500 });
      expect(http.post).toHaveBeenCalledWith(
        'http://ms-game:8083/games/live/1/join',
        { playerId: 5, eloBefore: 1500 },
      );
    });

    it('moveLiveGame manda uci y clocks', async () => {
      http.post.mockResolvedValue({});
      await service.moveLiveGame('5', '1', { uci: 'e2e4', clockWhiteMs: 599000 });
      expect(http.post).toHaveBeenCalledWith(
        expect.stringContaining('/move'),
        expect.objectContaining({ playerId: 5, uci: 'e2e4' }),
      );
    });

    it('resignLiveGame, drawLiveGame, timeoutLiveGame, rematchLiveGame mandan playerId', async () => {
      http.post.mockResolvedValue({});
      await service.resignLiveGame('5', '1');
      await service.drawLiveGame('5', '1');
      await service.timeoutLiveGame('5', '1');
      await service.rematchLiveGame('5', '1');
      const calls = http.post.mock.calls.map((c) => c[0] as string);
      expect(calls).toEqual(expect.arrayContaining([
        expect.stringContaining('/resign'),
        expect.stringContaining('/draw'),
        expect.stringContaining('/timeout'),
        expect.stringContaining('/rematch'),
      ]));
    });

    it('inviteToLiveGame manda header X-User-Id', async () => {
      http.post.mockResolvedValue({ matched: true });
      await service.inviteToLiveGame('5', '1', { email: 'a@b.cl', gameUrl: 'https://x' });
      expect(http.post).toHaveBeenCalledWith(
        expect.stringContaining('/invite'),
        { email: 'a@b.cl', gameUrl: 'https://x' },
        expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Id': '5' }) }),
      );
    });
  });

  // ── Notifications ───────────────────────────────────────────────────────

  describe('notifications', () => {
    it('listNotifications usa recipientId del usuario', async () => {
      http.get.mockResolvedValue([]);
      await service.listNotifications('5');
      expect(http.get).toHaveBeenCalledWith(
        expect.stringContaining('recipientId=5'),
        expect.objectContaining({ timeout: 5000 }),
      );
    });

    it('unreadNotificationCount delega con timeout corto', async () => {
      http.get.mockResolvedValue({ count: 3 });
      await service.unreadNotificationCount('5');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('unread-count'),
        expect.objectContaining({ timeout: 5000 }));
    });

    it('markNotificationRead llama PATCH /notifications/{id}/read', async () => {
      http.patch.mockResolvedValue({});
      await service.markNotificationRead('99');
      expect(http.patch).toHaveBeenCalledWith(
        'http://ms-notifications:8085/notifications/99/read', {},
      );
    });

    it('markAllNotificationsRead llama PATCH con recipientId', async () => {
      http.patch.mockResolvedValue({});
      await service.markAllNotificationsRead('5');
      expect(http.patch).toHaveBeenCalledWith(
        expect.stringContaining('read-all?recipientId=5'), {},
      );
    });
  });

  // ── Lichess ─────────────────────────────────────────────────────────────

  describe('getLichessProfile', () => {
    it('sin lichessUsername devuelve error informativo', async () => {
      http.get.mockResolvedValue({ lichessUsername: null });
      const r = await service.getLichessProfile('5');
      expect(r.username).toBeNull();
      expect(r.error).toBeDefined();
    });

    it('sincroniza con ms-users y devuelve ratings por modalidad', async () => {
      http.post.mockResolvedValue({
        lichessUsername: 'magnus',
        eloLichessBullet: 3200,
        eloLichessBlitz: 2950,
        eloLichessRapid: 2800,
        eloLichessClassical: null,
      });
      const r = await service.getLichessProfile('5');
      expect(r.username).toBe('magnus');
      expect(http.post).toHaveBeenCalledWith(
        expect.stringContaining('/users/5/lichess-sync'),
        {},
      );
      const ratings = (r.user as { ratings: { variant: string; rating: number }[] }).ratings;
      expect(ratings).toHaveLength(3); // classical null se omite
      expect(ratings.find((x) => x.variant === 'bullet')?.rating).toBe(3200);
    });

    it('si el sync falla devuelve error sin romper', async () => {
      http.post.mockImplementation(async () => {
        throw new Error('ms-users down');
      });
      const r = await service.getLichessProfile('5');
      expect(r.error).toBeDefined();
    });
  });

  // ── Tournaments ─────────────────────────────────────────────────────────

  describe('tournaments', () => {
    it('listTournaments filtra params undefined', async () => {
      http.get.mockResolvedValue({ content: [] });
      await service.listTournaments({ status: 'OPEN', format: undefined, search: '' });
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('status=OPEN'));
    });

    it('getTournament delega', async () => {
      http.get.mockResolvedValue({ id: 1 });
      await service.getTournament('1');
      expect(http.get).toHaveBeenCalledWith('http://ms-tournament:8082/tournaments/1');
    });

    it('getTournamentStandings delega', async () => {
      http.get.mockResolvedValue([]);
      await service.getTournamentStandings('1');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('/standings'));
    });

    it('getEnrichedRound enriquece pairings con nombres', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/rounds/1')) {
          return {
            id: 1, tournamentId: 1, roundNumber: 1, status: 'IN_PROGRESS',
            pairings: [{ id: 1, whitePlayerId: 5, blackPlayerId: 6 }],
          };
        }
        if (url.includes('/users/5/profile')) return { id: 5, firstName: 'A', lastName: 'B', eloFideStandard: 1800 };
        if (url.includes('/users/6/profile')) return { id: 6, firstName: 'C', lastName: 'D', eloNational: 1700 };
        return {};
      });
      const r = (await service.getEnrichedRound('1', '1')) as {
        pairings: Array<{ whitePlayerName: string; whitePlayerRating: number }>;
      };
      expect(r.pairings[0].whitePlayerName).toBe('A B');
      expect(r.pairings[0].whitePlayerRating).toBe(1800);
    });

    it('getMyRegistration devuelve null si la lista no es array', async () => {
      http.get.mockResolvedValue(null);
      const r = await service.getMyRegistration('1', '5');
      expect(r).toBeNull();
    });

    it('getMyRegistration filtra por playerId', async () => {
      http.get.mockResolvedValue([{ playerId: 5, status: 'CONFIRMED' }, { playerId: 6 }]);
      const r = (await service.getMyRegistration('1', '5')) as Record<string, unknown>;
      expect(r.status).toBe('CONFIRMED');
    });

    it('registerToTournament POSTea con playerId numérico', async () => {
      http.post.mockResolvedValue({ id: 1 });
      await service.registerToTournament('1', '5');
      expect(http.post).toHaveBeenCalledWith(
        'http://ms-tournament:8082/tournaments/1/registrations',
        { playerId: 5 },
      );
    });
  });

  // ── submitCasualGame ────────────────────────────────────────────────────

  describe('submitCasualGame', () => {
    it('inyecta whitePlayerId si falta', async () => {
      http.post.mockResolvedValue({ id: 1 });
      await service.submitCasualGame('5', { result: '1-0' });
      expect(http.post).toHaveBeenCalledWith(
        expect.stringContaining('/games'),
        expect.objectContaining({ gameType: 'CASUAL', whitePlayerId: 5 }),
      );
    });

    it('respeta whitePlayerId si viene en el body', async () => {
      http.post.mockResolvedValue({});
      await service.submitCasualGame('5', { whitePlayerId: 99, result: '1-0' });
      expect(http.post).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ whitePlayerId: 99 }),
      );
    });
  });
});
