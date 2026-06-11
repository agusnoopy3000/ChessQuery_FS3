import { Test, TestingModule } from '@nestjs/testing';
import { ServiceUnavailableException } from '@nestjs/common';
import { AdminService } from './admin.service';
import { UpstreamHttpService } from '../common/http.service';

describe('AdminService', () => {
  let service: AdminService;
  let http: {
    get: jest.Mock;
    post: jest.Mock;
    urls: Record<string, string>;
  };

  beforeEach(async () => {
    http = {
      get: jest.fn(),
      post: jest.fn(),
      urls: {
        msUsers: 'http://ms-users:8081',
        msTournament: 'http://ms-tournament:8082',
        msGame: 'http://ms-game:8083',
        msAnalytics: 'http://ms-analytics:8084',
        msEtl: 'http://ms-etl:8086',
      },
    };
    const module: TestingModule = await Test.createTestingModule({
      providers: [AdminService, { provide: UpstreamHttpService, useValue: http }],
    }).compile();
    service = module.get(AdminService);
  });

  describe('getDashboard', () => {
    it('arma el dashboard cuando todos los upstreams responden', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/users?')) return { totalElements: 42 };
        if (url.includes('/tournaments?')) return { content: [{ id: 1 }] };
        if (url.includes('/games?')) return { content: [] };
        if (url.includes('/analytics/')) return { games: 100 };
        if (url.includes('/etl/status')) return { lastRun: 'ok' };
        return {};
      });

      const r = await service.getDashboard();

      expect(r.users).toEqual({ total: 42 });
      expect(r.tournaments.active).toEqual({ content: [{ id: 1 }] });
      expect(r.analytics.platform).toEqual({ games: 100 });
      expect(r.etl.status).toEqual({ lastRun: 'ok' });
    });

    it('degrada con gracia si un upstream falla (ms-etl caído no rompe el resto)', async () => {
      http.get.mockImplementation(async (url: string) => {
        if (url.includes('/etl/status')) {
          throw new ServiceUnavailableException('etl down');
        }
        if (url.includes('/users?')) return { totalElements: 7 };
        return {};
      });

      const r = await service.getDashboard();

      expect(r.users).toEqual({ total: 7 });
      expect(r.etl.status).toBeNull();
      expect(r.etl.error).toBeDefined();
    });

    it('acepta total como alias de totalElements en la respuesta de usuarios', async () => {
      http.get.mockImplementation(async (url: string) =>
        url.includes('/users?') ? { total: 3 } : {},
      );

      const r = await service.getDashboard();

      expect(r.users).toEqual({ total: 3 });
    });
  });

  it('getEtlLogs usa el límite pedido', async () => {
    http.get.mockResolvedValue([]);
    await service.getEtlLogs(10);
    expect(http.get).toHaveBeenCalledWith('http://ms-etl:8086/etl/logs?limit=10');
  });

  it('triggerEtlSync escapa el nombre de la fuente en la URL', async () => {
    http.post.mockResolvedValue({ ok: true });
    await service.triggerEtlSync('lichess/teams');
    expect(http.post).toHaveBeenCalledWith(
      'http://ms-etl:8086/etl/sync/lichess%2Fteams',
      {},
    );
  });

  it('searchUsers escapa el término de búsqueda', async () => {
    http.get.mockResolvedValue([]);
    await service.searchUsers('pérez & co');
    expect(http.get).toHaveBeenCalledWith(
      expect.stringContaining(`/users/search?q=${encodeURIComponent('pérez & co')}`),
    );
  });

  it('propaga el error si un endpoint directo de ETL falla', async () => {
    http.get.mockRejectedValue(new ServiceUnavailableException('down'));
    await expect(service.getEtlStatus()).rejects.toBeInstanceOf(ServiceUnavailableException);
  });
});
