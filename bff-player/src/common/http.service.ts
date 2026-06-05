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
    const op = () => firstValueFrom(this.http.get<T>(url, { timeout: 15000, ...config })).then((r) => r.data);
    try { return await op(); }
    catch (err) {
      try { return await this.withRetry(op, err as AxiosError); }
      catch (err2) { this.rethrow(err2 as AxiosError, 'GET', url); }
    }
  }

  async post<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    const op = () => firstValueFrom(this.http.post<T>(url, body, { timeout: 15000, ...config })).then((r) => r.data);
    try { return await op(); }
    catch (err) {
      try { return await this.withRetry(op, err as AxiosError); }
      catch (err2) { this.rethrow(err2 as AxiosError, 'POST', url); }
    }
  }

  async patch<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    const op = () => firstValueFrom(this.http.patch<T>(url, body, { timeout: 15000, ...config })).then((r) => r.data);
    try { return await op(); }
    catch (err) {
      try { return await this.withRetry(op, err as AxiosError); }
      catch (err2) { this.rethrow(err2 as AxiosError, 'PATCH', url); }
    }
  }

  async put<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    const op = () => firstValueFrom(this.http.put<T>(url, body, { timeout: 15000, ...config })).then((r) => r.data);
    try { return await op(); }
    catch (err) {
      try { return await this.withRetry(op, err as AxiosError); }
      catch (err2) { this.rethrow(err2 as AxiosError, 'PUT', url); }
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
