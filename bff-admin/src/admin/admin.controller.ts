import {
  BadRequestException,
  Controller,
  ForbiddenException,
  Get,
  Param,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Request } from 'express';
import { AdminService } from './admin.service';
import { AuthGuard, getUserRole } from '../common/auth.guard';

@Controller('admin')
@UseGuards(AuthGuard)
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  private assertAdmin(req: Request) {
    const role = getUserRole(req);
    if (role !== 'ADMIN') {
      throw new ForbiddenException('ADMIN role required');
    }
  }

  @Get('dashboard')
  async dashboard(@Req() req: Request) {
    this.assertAdmin(req);
    return this.adminService.getDashboard();
  }

  @Get('etl/status')
  async etlStatus(@Req() req: Request) {
    this.assertAdmin(req);
    return this.adminService.getEtlStatus();
  }

  @Post('etl/sync/:source')
  async etlSync(@Req() req: Request, @Param('source') source: string) {
    this.assertAdmin(req);
    return this.adminService.triggerEtlSync(source);
  }

  @Get('users')
  async users(@Req() req: Request, @Query('q') q?: string) {
    this.assertAdmin(req);
    if (!q || q.trim().length === 0) {
      throw new BadRequestException('Query parameter "q" is required');
    }
    return this.adminService.searchUsers(q);
  }
}
