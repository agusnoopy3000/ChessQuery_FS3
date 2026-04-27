import { Body, Controller, Post } from '@nestjs/common';
import { PlayerService } from './player.service';

interface SyncBody {
  id: number;
  email?: string;
  firstName?: string;
  lastName?: string;
  lichessUsername?: string;
}

/**
 * Endpoint público para sincronizar el AuthUser recién registrado con su
 * Player en ms-users. Se llama desde el portal inmediatamente después de
 * /auth/register, antes de que exista un JWT con sentido. Por eso queda
 * fuera del AuthGuard que protege el resto del bff-player.
 */
@Controller('player')
export class SyncController {
  constructor(private readonly playerService: PlayerService) {}

  @Post('sync')
  async syncProfile(@Body() body: SyncBody) {
    return this.playerService.syncFromAuth(body);
  }
}
