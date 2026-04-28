import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Request } from 'express';
import { PlayerService } from './player.service';
import { AuthGuard, getUserId } from '../common/auth.guard';

@Controller('player')
@UseGuards(AuthGuard)
export class PlayerController {
  constructor(private readonly playerService: PlayerService) {}

  @Get('me/dashboard')
  async myDashboard(@Req() req: Request) {
    const userId = getUserId(req);
    return this.playerService.getDashboard(userId);
  }

  @Get('me/rating-chart')
  async myRatingChart(
    @Req() req: Request,
    @Query('type') type = 'FIDE_STANDARD',
    @Query('months') months = '12',
  ) {
    const userId = getUserId(req);
    const m = parseInt(months, 10);
    if (Number.isNaN(m) || m <= 0 || m > 60) {
      throw new BadRequestException('months must be between 1 and 60');
    }
    return this.playerService.getRatingChart(userId, type, m);
  }

  @Get(':id/profile')
  async publicProfile(@Param('id') id: string) {
    return this.playerService.getPublicProfile(id);
  }

  @Get(':id/lichess')
  async lichessProfile(@Param('id') id: string) {
    return this.playerService.getLichessProfile(id);
  }

  @Get('search')
  async search(@Query('q') q?: string) {
    if (!q || q.trim().length === 0) {
      throw new BadRequestException('Query parameter "q" is required');
    }
    return this.playerService.searchPlayers(q);
  }

  @Get('rankings')
  async rankings(
    @Query('category') category?: string,
    @Query('region') region?: string,
  ) {
    return this.playerService.getRankings(category, region);
  }

  /**
   * Encuentra un rival random (preferentemente con rating similar) para
   * que el jugador autenticado pueda iniciar una partida.
   */
  @Post('play/find-match')
  async findMatch(@Req() req: Request) {
    const userId = getUserId(req);
    return this.playerService.findRandomOpponent(userId);
  }

  /**
   * Persiste el resultado de una partida casual jugada en ChessQuery.
   * Reenvía la creación a ms-game (que recalcula ELO y persiste PGN
   * mínimo si se proveyó).
   */
  @Post('play/games')
  async submitGame(@Req() req: Request, @Body() body: Record<string, unknown>) {
    const userId = getUserId(req);
    return this.playerService.submitCasualGame(userId, body);
  }
}
