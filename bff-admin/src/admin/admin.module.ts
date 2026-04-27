import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { AdminController } from './admin.controller';
import { AdminService } from './admin.service';
import { UpstreamHttpService } from '../common/http.service';

@Module({
  imports: [HttpModule.register({ timeout: 5000 })],
  controllers: [AdminController],
  providers: [AdminService, UpstreamHttpService],
})
export class AdminModule {}
