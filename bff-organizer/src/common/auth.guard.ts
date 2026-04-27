import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Request } from 'express';

@Injectable()
export class AuthGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request>();
    const userId = req.headers['x-user-id'];
    if (!userId || typeof userId !== 'string') {
      throw new UnauthorizedException('Missing X-User-Id header');
    }
    return true;
  }
}

export function getUserId(req: Request): string {
  const userId = req.headers['x-user-id'];
  if (!userId || typeof userId !== 'string') {
    throw new UnauthorizedException('Missing X-User-Id header');
  }
  return userId;
}

export function getUserRole(req: Request): string | undefined {
  const role = req.headers['x-user-role'];
  return typeof role === 'string' ? role : undefined;
}
