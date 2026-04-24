import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { PlayerController } from './player.controller';
import { PlayerService } from './player.service';
import { UpstreamHttpService } from '../common/http.service';

@Module({
  imports: [HttpModule.register({ timeout: 5000 })],
  controllers: [PlayerController],
  providers: [PlayerService, UpstreamHttpService],
})
export class PlayerModule {}
