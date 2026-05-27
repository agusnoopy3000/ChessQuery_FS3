import { Test, TestingModule } from '@nestjs/testing';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import {
  HttpException,
  ServiceUnavailableException,
} from '@nestjs/common';
import { of, throwError } from 'rxjs';
import { AxiosError, AxiosResponse } from 'axios';
import { UpstreamHttpService } from './http.service';

/**
 * Tests unitarios de UpstreamHttpService.
 *
 * Verifica:
 *  - get/post/patch/put devuelven response.data.
 *  - 4xx/5xx upstream se traducen a HttpException con el mismo status y body.
 *  - Errores de red transitorios (ECONNREFUSED, ENOTFOUND, etc.) reintentan 1 vez.
 *  - Errores no transitorios devuelven ServiceUnavailableException.
 */
describe('UpstreamHttpService', () => {
  let svc: UpstreamHttpService;
  let http: { get: jest.Mock; post: jest.Mock; patch: jest.Mock; put: jest.Mock };

  beforeEach(async () => {
    http = {
      get: jest.fn(),
      post: jest.fn(),
      patch: jest.fn(),
      put: jest.fn(),
    };
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

  const okResponse = <T>(data: T): AxiosResponse<T> => ({
    data, status: 200, statusText: 'OK', headers: {}, config: { headers: {} } as never,
  });

  it('expone las URLs por defecto desde ConfigService', () => {
    expect(svc.urls.msUsers).toBe('http://ms-users:8081');
    expect(svc.urls.msNotifications).toBe('http://ms-notifications:8085');
  });

  it('get devuelve data de la respuesta', async () => {
    http.get.mockReturnValue(of(okResponse({ id: 1 })));
    await expect(svc.get('/x')).resolves.toEqual({ id: 1 });
  });

  it('post devuelve data de la respuesta', async () => {
    http.post.mockReturnValue(of(okResponse({ ok: true })));
    await expect(svc.post('/x', { a: 1 })).resolves.toEqual({ ok: true });
  });

  it('patch devuelve data', async () => {
    http.patch.mockReturnValue(of(okResponse({ k: 'v' })));
    await expect(svc.patch('/x', {})).resolves.toEqual({ k: 'v' });
  });

  it('put devuelve data', async () => {
    http.put.mockReturnValue(of(okResponse({ id: 9 })));
    await expect(svc.put('/x', {})).resolves.toEqual({ id: 9 });
  });

  it('4xx upstream se traduce a HttpException con mismo status', async () => {
    const err: Partial<AxiosError> = {
      response: { status: 404, data: { message: 'not found' }, statusText: 'Not Found',
        headers: {}, config: { headers: {} } as never },
      message: 'Request failed',
    };
    http.get.mockReturnValue(throwError(() => err));
    await expect(svc.get('/missing')).rejects.toMatchObject({ status: 404 });
  });

  it('errores de red transitorios reintentan 1 vez antes de fallar', async () => {
    const err: Partial<AxiosError> = { code: 'ECONNRESET', message: 'reset' };
    http.get.mockReturnValue(throwError(() => err));
    await expect(svc.get('/x')).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(http.get).toHaveBeenCalledTimes(2);
  });

  it('reintento exitoso devuelve la data del segundo intento', async () => {
    const err: Partial<AxiosError> = { code: 'ENOTFOUND', message: 'dns' };
    http.get
      .mockReturnValueOnce(throwError(() => err))
      .mockReturnValueOnce(of(okResponse({ id: 1 })));
    await expect(svc.get('/x')).resolves.toEqual({ id: 1 });
  });

  it('errores no transitorios no reintentan', async () => {
    const err: Partial<AxiosError> = { code: 'EOTHER', message: 'random', response: undefined };
    http.post.mockReturnValue(throwError(() => err));
    await expect(svc.post('/x', {})).rejects.toBeInstanceOf(ServiceUnavailableException);
    expect(http.post).toHaveBeenCalledTimes(1);
  });

  it('errores con response pero sin message en body usan el message del error', async () => {
    const err: Partial<AxiosError> = {
      response: { status: 400, data: {}, statusText: 'Bad', headers: {}, config: { headers: {} } as never },
      message: 'Bad Request',
    };
    http.patch.mockReturnValue(throwError(() => err));
    await expect(svc.patch('/x', {})).rejects.toBeInstanceOf(HttpException);
  });
});
