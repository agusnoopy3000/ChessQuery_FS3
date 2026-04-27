import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Request } from 'express';
import { OrganizerService } from './organizer.service';
import { AuthGuard, getUserId } from '../common/auth.guard';

@Controller('organizer')
@UseGuards(AuthGuard)
export class OrganizerController {
  constructor(private readonly organizerService: OrganizerService) {}

  @Get('tournaments')
  async list(@Req() req: Request, @Query() query: Record<string, string>) {
    const userId = getUserId(req);
    return this.organizerService.listTournaments(userId, query);
  }

  @Get('tournaments/:id')
  async detail(@Param('id') id: string) {
    return this.organizerService.getTournament(id);
  }

  @Post('tournaments')
  async create(@Req() req: Request, @Body() body: Record<string, unknown>) {
    const userId = getUserId(req);
    return this.organizerService.createTournament(userId, body);
  }

  @Get('tournaments/:id/round/:n')
  async round(@Param('id') id: string, @Param('n') n: string) {
    return this.organizerService.getEnrichedRound(id, n);
  }

  @Post('tournaments/:id/join')
  async join(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
  ) {
    const userId = getUserId(req);
    return this.organizerService.join(id, userId, body);
  }

  @Post('tournaments/:id/rounds/:n/generate')
  async generateRound(@Req() req: Request, @Param('id') id: string, @Param('n') n: string) {
    const userId = getUserId(req);
    return this.organizerService.generateRound(id, n, userId);
  }

  @Patch('pairings/:pid/result')
  async patchResult(@Param('pid') pid: string, @Body() body: Record<string, unknown>) {
    return this.organizerService.patchPairingResult(pid, body);
  }

  @Get('tournaments/:id/standings')
  async standings(@Param('id') id: string) {
    return this.organizerService.getStandings(id);
  }
}
