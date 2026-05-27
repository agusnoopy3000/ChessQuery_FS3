import { Test, TestingModule } from '@nestjs/testing';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { HttpException, ServiceUnavailableException } from '@nestjs/common';
import { of, throwError } from 'rxjs';
import { AxiosError, AxiosResponse } from 'axios';
import { UpstreamHttpService } from './http.service';

describe('UpstreamHttpService (organizer)', () => {
  let svc: UpstreamHttpService;
  let http: { get: jest.Mock; post: jest.Mock; patch: jest.Mock; delete: jest.Mock };

  beforeEach(async () => {
    http = { get: jest.fn(), post: jest.fn(), patch: jest.fn(), delete: jest.fn() };
    const config = { get: jest.fn((_k: string, def: string) => def) };
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        UpstreamHttpService,
        { provide: HttpService, useValue: http },
        { provide: ConfigService, useValue: config },
      ],
    }).compile();
    svc = module.get(UpstreamHttpService);
  });

  const ok = <T>(data: T): AxiosResponse<T> => ({
    data, status: 200, statusText: 'OK', headers: {}, config: { headers: {} } as never,
  });

  it('expone urls por defecto', () => {
    expect(svc.urls.msTournament).toBe('http://ms-tournament:8082');
  });

  it('get/post/patch/delete devuelven data', async () => {
    http.get.mockReturnValue(of(ok({ a: 1 })));
    http.post.mockReturnValue(of(ok({ b: 2 })));
    http.patch.mockReturnValue(of(ok({ c: 3 })));
    http.delete.mockReturnValue(of(ok({ d: 4 })));
    expect(await svc.get('/')).toEqual({ a: 1 });
    expect(await svc.post('/', {})).toEqual({ b: 2 });
    expect(await svc.patch('/', {})).toEqual({ c: 3 });
    expect(await svc.delete('/')).toEqual({ d: 4 });
  });

  it('error 4xx upstream se traduce a HttpException', async () => {
    const err: Partial<AxiosError> = {
      response: { status: 404, data: { message: 'nf' }, statusText: 'NF', headers: {}, config: { headers: {} } as never },
      message: 'failed',
    };
    http.get.mockReturnValue(throwError(() => err));
    await expect(svc.get('/x')).rejects.toMatchObject({ status: 404 });
  });

  it('error transitorio reintenta una vez', async () => {
    const err: Partial<AxiosError> = { code: 'ECONNRESET', message: 'r' };
    http.get.mockReturnValue(throwError(() => err));
    await expect(svc.get('/x')).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(http.get).toHaveBeenCalledTimes(2);
  });

  it('error no transitorio no reintenta', async () => {
    const err: Partial<AxiosError> = { code: 'EOTHER', message: 'o' };
    http.post.mockReturnValue(throwError(() => err));
    await expect(svc.post('/x', {})).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(http.post).toHaveBeenCalledTimes(1);
  });

  it('error con response y sin message en body usa message del error', async () => {
    const err: Partial<AxiosError> = {
      response: { status: 400, data: {}, statusText: 'Bad', headers: {}, config: { headers: {} } as never },
      message: 'Bad',
    };
    http.patch.mockReturnValue(throwError(() => err));
    await expect(svc.patch('/x', {})).rejects.toBeInstanceOf(HttpException);
  });
});
