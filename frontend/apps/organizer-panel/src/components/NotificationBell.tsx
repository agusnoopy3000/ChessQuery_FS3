import { NotificationBell as Bell } from '@chessquery/ui-lib/src/components/NotificationBell';
import { organizerApi } from '../api';

export const NotificationBell = () => (
  <Bell
    listNotifications={organizerApi.listNotifications}
    markAllRead={organizerApi.markAllNotificationsRead}
  />
);
