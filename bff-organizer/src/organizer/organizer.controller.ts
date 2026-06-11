import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
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
  async patchResult(
    @Req() req: Request,
    @Param('pid') pid: string,
    @Body() body: Record<string, unknown>,
  ) {
    const userId = getUserId(req);
    return this.organizerService.patchPairingResult(pid, userId, body);
  }

  @Get('tournaments/:id/standings')
  async standings(@Param('id') id: string) {
    return this.organizerService.getStandings(id);
  }

  @Get('tournaments/:id/registrations')
  async listRegistrations(@Param('id') id: string) {
    return this.organizerService.listRegistrations(id);
  }

  @Patch('registrations/:rid/approve')
  async approveRegistration(@Req() req: Request, @Param('rid') rid: string) {
    const userId = getUserId(req);
    return this.organizerService.approveRegistration(rid, userId);
  }

  @Patch('registrations/:rid/reject')
  async rejectRegistration(
    @Req() req: Request,
    @Param('rid') rid: string,
    @Body() body: Record<string, unknown>,
  ) {
    const userId = getUserId(req);
    return this.organizerService.rejectRegistration(rid, userId, body);
  }

  @Delete('tournaments/:id')
  @HttpCode(204)
  async deleteTournament(@Req() req: Request, @Param('id') id: string) {
    const userId = getUserId(req);
    return this.organizerService.deleteTournament(id, userId);
  }

  @Patch('tournaments/:id/status')
  async patchStatus(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: Record<string, unknown>,
  ) {
    const userId = getUserId(req);
    return this.organizerService.patchTournamentStatus(id, userId, body);
  }

  // ── Notificaciones (mismo backend que el jugador) ────────────────────────

  @Get('notifications')
  async listNotifications(@Req() req: Request) {
    const userId = getUserId(req);
    return this.organizerService.listNotifications(userId);
  }

  @Get('notifications/unread-count')
  async unreadNotificationCount(@Req() req: Request) {
    const userId = getUserId(req);
    return this.organizerService.unreadNotificationCount(userId);
  }

  @Post('notifications/:id/read')
  async markNotificationRead(@Param('id') id: string) {
    return this.organizerService.markNotificationRead(id);
  }

  @Post('notifications/read-all')
  async markAllNotificationsRead(@Req() req: Request) {
    const userId = getUserId(req);
    return this.organizerService.markAllNotificationsRead(userId);
  }
}
