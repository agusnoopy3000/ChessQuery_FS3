import { useNavigate } from 'react-router-dom';
import { NotificationBell as Bell, type NotificationItem } from '@chessquery/ui-lib/src/components/NotificationBell';
import { playerApi } from '../api';

/** Notificaciones accionables del jugador → ruta destino. */
const resolveLink = (n: NotificationItem): string | null => {
  if (n.eventType === 'game.invitation' && n.payload) {
    try {
      const p = JSON.parse(n.payload) as { gameId?: number | string };
      if (p.gameId != null) return `/play/${p.gameId}`;
    } catch { /* ignore */ }
  }
  if (n.eventType === 'registration.approved' && n.payload) {
    try {
      const p = JSON.parse(n.payload) as { tournamentId?: number };
      if (p.tournamentId != null) return `/tournaments/${p.tournamentId}`;
    } catch { /* ignore */ }
  }
  return null;
};

export const NotificationBell = () => {
  const navigate = useNavigate();
  return (
    <Bell
      listNotifications={playerApi.listNotifications}
      markAllRead={playerApi.markAllNotificationsRead}
      resolveLink={resolveLink}
      onNavigate={navigate}
    />
  );
};
