import {
  HttpException,
  Injectable,
  Logger,
  ServiceUnavailableException,
} from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { ConfigService } from '@nestjs/config';
import { firstValueFrom } from 'rxjs';
import { AxiosError, AxiosRequestConfig } from 'axios';

export interface Upstreams {
  msUsers: string;
  msTournament: string;
  msGame: string;
  msAnalytics: string;
  msNotifications: string;
}

/**
 * Timeout por request hacia los microservicios. Es el MISMO en los 3 BFFs
 * (mantener sincronizado si se cambia): 15s cubre las rutas lentas (sync
 * Lichess, generación de rondas) y queda por debajo del response-timeout
 * de 30s del API Gateway.
 */
const UPSTREAM_TIMEOUT_MS = 15000;

type HttpMethod = 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';

/**
 * Cliente HTTP hacia los microservicios con timeout y un único reintento
 * ante errores de red transitorios.
 *
 * ⚠️ Este archivo está replicado en bff-player, bff-organizer y bff-admin
 * (solo difiere la interfaz {@link Upstreams}). Cualquier cambio acá debe
 * aplicarse en las 3 copias hasta que se extraiga a un paquete común.
 */
@Injectable()
export class UpstreamHttpService {
  private readonly logger = new Logger(UpstreamHttpService.name);
  public readonly urls: Upstreams;

  constructor(
    private readonly http: HttpService,
    private readonly config: ConfigService,
  ) {
    this.urls = {
      msUsers: this.config.get<string>('MS_USERS_URL', 'http://ms-users:8081'),
      msTournament: this.config.get<string>('MS_TOURNAMENT_URL', 'http://ms-tournament:8082'),
      msGame: this.config.get<string>('MS_GAME_URL', 'http://ms-game:8083'),
      msAnalytics: this.config.get<string>('MS_ANALYTICS_URL', 'http://ms-analytics:8084'),
      msNotifications: this.config.get<string>('MS_NOTIFICATIONS_URL', 'http://ms-notifications:8085'),
    };
  }

  /**
   * Errores de red transitorios que justifican un único reintento (~300ms).
   * Cubre el caso de restart de un MS en docker: el container nuevo recibe
   * una IP distinta y los pools de conexión cacheados fallan por una ventana
   * corta con ENOTFOUND/ECONNREFUSED hasta que el DNS se estabiliza.
   */
  private static readonly TRANSIENT_CODES = new Set([
    'ECONNREFUSED',
    'ENOTFOUND',
    'EAI_AGAIN',
    'ETIMEDOUT',
    'ECONNRESET',
  ]);

  private isTransient(err: AxiosError): boolean {
    return !err.response && !!err.code && UpstreamHttpService.TRANSIENT_CODES.has(err.code);
  }

  private async withRetry<T>(op: () => Promise<T>, err: AxiosError): Promise<T> {
    if (!this.isTransient(err)) throw err;
    await new Promise((r) => setTimeout(r, 300));
    return op();
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>('GET', url, config);
  }

  async post<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>('POST', url, config, body);
  }

  async patch<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>('PATCH', url, config, body);
  }

  async put<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>('PUT', url, config, body);
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>('DELETE', url, config);
  }

  private async request<T>(
    method: HttpMethod,
    url: string,
    config?: AxiosRequestConfig,
    body?: unknown,
  ): Promise<T> {
    const merged: AxiosRequestConfig = { timeout: UPSTREAM_TIMEOUT_MS, ...config };
    const op = (): Promise<T> => {
      switch (method) {
        case 'GET':
          return firstValueFrom(this.http.get<T>(url, merged)).then((r) => r.data);
        case 'DELETE':
          return firstValueFrom(this.http.delete<T>(url, merged)).then((r) => r.data);
        case 'POST':
          return firstValueFrom(this.http.post<T>(url, body, merged)).then((r) => r.data);
        case 'PATCH':
          return firstValueFrom(this.http.patch<T>(url, body, merged)).then((r) => r.data);
        case 'PUT':
          return firstValueFrom(this.http.put<T>(url, body, merged)).then((r) => r.data);
      }
    };
    try {
      return await op();
    } catch (err) {
      try {
        return await this.withRetry(op, err as AxiosError);
      } catch (err2) {
        this.rethrow(err2 as AxiosError, method, url);
      }
    }
  }

  private rethrow(err: AxiosError, method: string, url: string): never {
    if (err.response) {
      const status = err.response.status;
      const data = err.response.data as Record<string, unknown> | undefined;
      const message =
        (data && typeof data['message'] === 'string' ? (data['message'] as string) : undefined) ??
        err.message;
      throw new HttpException(data ?? { message }, status);
    }
    this.logger.warn(`Upstream ${method} ${url} failed: ${err.message}`);
    throw new ServiceUnavailableException({
      message: `Upstream service at ${url} is unavailable`,
      code: err.code,
    });
  }
}
