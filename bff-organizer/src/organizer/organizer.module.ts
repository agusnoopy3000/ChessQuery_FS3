import { Module } from '@nestjs/common';
import { HttpModule } from '@nestjs/axios';
import { OrganizerController } from './organizer.controller';
import { OrganizerService } from './organizer.service';
import { UpstreamHttpService } from '../common/http.service';

@Module({
  imports: [HttpModule.register({ timeout: 5000 })],
  controllers: [OrganizerController],
  providers: [OrganizerService, UpstreamHttpService],
})
export class OrganizerModule {}
