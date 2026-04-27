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
  msEtl: string;
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
      msEtl: this.config.get<string>('MS_ETL_URL', 'http://ms-etl:8086'),
    };
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    try {
      const res = await firstValueFrom(this.http.get<T>(url, { timeout: 5000, ...config }));
      return res.data;
    } catch (err) {
      this.rethrow(err as AxiosError, 'GET', url);
    }
  }

  async post<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    try {
      const res = await firstValueFrom(this.http.post<T>(url, body, { timeout: 5000, ...config }));
      return res.data;
    } catch (err) {
      this.rethrow(err as AxiosError, 'POST', url);
    }
  }

  async patch<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    try {
      const res = await firstValueFrom(this.http.patch<T>(url, body, { timeout: 5000, ...config }));
      return res.data;
    } catch (err) {
      this.rethrow(err as AxiosError, 'PATCH', url);
    }
  }

  async put<T>(url: string, body: unknown, config?: AxiosRequestConfig): Promise<T> {
    try {
      const res = await firstValueFrom(this.http.put<T>(url, body, { timeout: 5000, ...config }));
      return res.data;
    } catch (err) {
      this.rethrow(err as AxiosError, 'PUT', url);
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
