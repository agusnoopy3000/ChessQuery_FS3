import { Test, TestingModule } from '@nestjs/testing';
import { ServiceUnavailableException } from '@nestjs/common';
import { OrganizerService } from './organizer.service';
import { UpstreamHttpService } from '../common/http.service';

describe('OrganizerService', () => {
  let service: OrganizerService;
  let http: {
    get: jest.Mock; post: jest.Mock; patch: jest.Mock; delete: jest.Mock;
    urls: Record<string, string>;
  };

  beforeEach(async () => {
    http = {
      get: jest.fn(), post: jest.fn(), patch: jest.fn(), delete: jest.fn(),
      urls: {
        msTournament: 'http://ms-tournament:8082',
        msUsers: 'http://ms-users:8081',
        msNotifications: 'http://ms-notifications:8085',
      },
    };
    const module: TestingModule = await Test.createTestingModule({
      providers: [OrganizerService, { provide: UpstreamHttpService, useValue: http }],
    }).compile();
    service = module.get(OrganizerService);
  });

  describe('listTournaments', () => {
    it('filtra solo los del organizerId solicitado', async () => {
      http.get.mockResolvedValue({
        content: [{ id: 1, organizerId: 9 }, { id: 2, organizerId: 7 }],
      });
      const r = (await service.listTournaments('9', {})) as { content: unknown[]; totalElements: number };
      expect(r.content).toHaveLength(1);
      expect(r.totalElements).toBe(1);
    });

    it('si content no es array devuelve la respuesta tal cual', async () => {
      http.get.mockResolvedValue({ foo: 'bar' });
      const r = await service.listTournaments('9', {});
      expect(r).toEqual({ foo: 'bar' });
    });

    it('mezcla query params adicionales en la URL', async () => {
      http.get.mockResolvedValue({ content: [] });
      await service.listTournaments('9', { status: 'OPEN', page: '0' });
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('status=OPEN'));
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('page=0'));
    });
  });

  it('getTournament propaga 503 si ms-tournament está caído', async () => {
    http.get.mockRejectedValue(new ServiceUnavailableException('down'));
    await expect(service.getTournament('1')).rejects.toBeInstanceOf(ServiceUnavailableException);
  });

  it('createTournament añade organizerId numérico y headers ORGANIZER', async () => {
    http.post.mockResolvedValue({ id: 1 });
    await service.createTournament('9', { name: 'T' });
    expect(http.post).toHaveBeenCalledWith(
      expect.stringContaining('/tournaments'),
      expect.objectContaining({ name: 'T', organizerId: 9 }),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER', 'X-User-Id': '9' }),
      }),
    );
  });

  describe('getEnrichedRound', () => {
    it('enriquece pairings con nombres y ratings', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/rounds/')) {
          return { id: 1, tournamentId: 1, roundNumber: 1, status: 'IN_PROGRESS',
            pairings: [{ id: 1, whitePlayerId: 5, blackPlayerId: 6 }] };
        }
        if (url.includes('/users/5/profile')) return { id: 5, firstName: 'A', lastName: 'B', eloFideStandard: 1800 };
        if (url.includes('/users/6/profile')) return { id: 6, firstName: 'C', lastName: 'D', eloNational: 1700 };
        return {};
      });
      const r = await service.getEnrichedRound('1', '1');
      expect(r.pairings[0].whitePlayerName).toBe('A B');
      expect(r.pairings[0].whitePlayerRating).toBe(1800);
      expect(r.pairings[0].blackPlayerRating).toBe(1700);
    });

    it('cuando un profile falla cae a undefined sin romper', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/rounds/')) {
          return { id: 1, tournamentId: 1, roundNumber: 1, status: 'X',
            pairings: [{ id: 1, whitePlayerId: 5, blackPlayerId: null }] };
        }
        if (url.includes('/users/')) throw new Error('users down');
        return {};
      });
      const r = await service.getEnrichedRound('1', '1');
      expect(r.pairings[0].whitePlayerName).toBeUndefined();
      expect(r.pairings[0].blackPlayerName).toBeUndefined();
    });
  });

  it('join inyecta playerId del userId si no viene en el body, con identidad en headers', async () => {
    http.post.mockResolvedValue({});
    await service.join('1', '5', {});
    expect(http.post).toHaveBeenCalledWith(
      expect.stringContaining('/registrations'),
      expect.objectContaining({ playerId: 5 }),
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER', 'X-User-Id': '5' }),
      }),
    );
  });

  describe('generateRound', () => {
    it('si el torneo está OPEN, lo transiciona a IN_PROGRESS antes de generar', async () => {
      http.get.mockResolvedValue({ status: 'OPEN' });
      http.patch.mockResolvedValue({});
      http.post.mockResolvedValue({});
      await service.generateRound('1', '1', '9');
      expect(http.patch).toHaveBeenCalledWith(
        expect.stringContaining('/status'),
        { newStatus: 'IN_PROGRESS' },
        expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER' }) }),
      );
      expect(http.post).toHaveBeenCalledWith(
        expect.stringContaining('/rounds/1'),
        {},
        expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Id': '9' }) }),
      );
    });

    it('si el torneo está IN_PROGRESS no patchea status', async () => {
      http.get.mockResolvedValue({ status: 'IN_PROGRESS' });
      http.post.mockResolvedValue({});
      await service.generateRound('1', '1', '9');
      expect(http.patch).not.toHaveBeenCalled();
    });
  });

  it('patchPairingResult delega a ms-tournament con identidad del organizador', async () => {
    http.patch.mockResolvedValue({});
    await service.patchPairingResult('99', '9', { result: '1-0' });
    expect(http.patch).toHaveBeenCalledWith(
      expect.stringContaining('/pairings/99/result'),
      { result: '1-0' },
      expect.objectContaining({
        headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER', 'X-User-Id': '9' }),
      }),
    );
  });

  it('getStandings delega', async () => {
    http.get.mockResolvedValue([]);
    await service.getStandings('1');
    expect(http.get).toHaveBeenCalledWith(expect.stringContaining('/standings'));
  });

  describe('listRegistrations', () => {
    it('enriquece con playerName y ratings', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/registrations')) {
          return [{ id: 1, playerId: 5 }, { id: 2, playerId: 6 }];
        }
        if (url.includes('/users/5/profile')) return { id: 5, firstName: 'A', lastName: 'B', eloFideStandard: 1800 };
        if (url.includes('/users/6/profile')) return { id: 6, firstName: 'C', lastName: 'D' };
        return {};
      });
      const r = (await service.listRegistrations('1')) as Array<Record<string, unknown>>;
      expect(r[0].playerName).toBe('A B');
      expect(r[0].playerEloFide).toBe(1800);
    });

    it('si la respuesta no es array la devuelve tal cual', async () => {
      http.get.mockResolvedValue(null);
      const r = await service.listRegistrations('1');
      expect(r).toBeNull();
    });
  });

  it('approveRegistration manda headers organizer', async () => {
    http.patch.mockResolvedValue({});
    await service.approveRegistration('1', '9');
    expect(http.patch).toHaveBeenCalledWith(
      expect.stringContaining('/approve'),
      {},
      expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Id': '9' }) }),
    );
  });

  it('rejectRegistration pasa body con reason', async () => {
    http.patch.mockResolvedValue({});
    await service.rejectRegistration('1', '9', { reason: 'bad' });
    expect(http.patch).toHaveBeenCalledWith(
      expect.stringContaining('/reject'),
      { reason: 'bad' },
      expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER' }) }),
    );
  });

  it('deleteTournament delega DELETE con headers', async () => {
    http.delete.mockResolvedValue({});
    await service.deleteTournament('1', '9');
    expect(http.delete).toHaveBeenCalledWith(
      'http://ms-tournament:8082/tournaments/1',
      expect.objectContaining({ headers: expect.objectContaining({ 'X-User-Role': 'ORGANIZER' }) }),
    );
  });

  it('patchTournamentStatus delega con headers', async () => {
    http.patch.mockResolvedValue({});
    await service.patchTournamentStatus('1', '9', { newStatus: 'OPEN' });
    expect(http.patch).toHaveBeenCalledWith(
      expect.stringContaining('/status'),
      { newStatus: 'OPEN' },
      expect.any(Object),
    );
  });

  describe('notifications', () => {
    it('listNotifications usa recipientId', async () => {
      http.get.mockResolvedValue([]);
      await service.listNotifications('9');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('recipientId=9'));
    });

    it('unreadNotificationCount delega', async () => {
      http.get.mockResolvedValue({ count: 0 });
      await service.unreadNotificationCount('9');
      expect(http.get).toHaveBeenCalledWith(expect.stringContaining('unread-count'));
    });

    it('markNotificationRead delega PATCH', async () => {
      http.patch.mockResolvedValue({});
      await service.markNotificationRead('99');
      expect(http.patch).toHaveBeenCalledWith(expect.stringContaining('/99/read'), {});
    });

    it('markAllNotificationsRead delega PATCH', async () => {
      http.patch.mockResolvedValue({});
      await service.markAllNotificationsRead('9');
      expect(http.patch).toHaveBeenCalledWith(expect.stringContaining('read-all?recipientId=9'), {});
    });
  });
});
