import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Shell, ShellNavItem } from '@chessquery/ui-lib';

import { LoginPage } from './pages/Login';
import { OrganizerPortalPage } from './pages/OrganizerPortal';
import { OrganizerTournamentsPage } from './pages/OrganizerTournaments';
import { TournamentDetailPage } from './pages/TournamentDetail';
import { NotificationBell } from './components/NotificationBell';

const buildNav = (pathname: string, navigate: ReturnType<typeof useNavigate>): ShellNavItem[] => [
  { id: 'home', label: 'Inicio', icon: '♖', desc: 'Resumen del organizador', active: pathname === '/', onClick: () => navigate('/') },
  { id: 'tournaments', label: 'Torneos', icon: '♜', desc: 'Crear y gestionar torneos', active: pathname.startsWith('/tournaments'), onClick: () => navigate('/tournaments') },
];

export const App = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, loading } = useAuth();

  if (location.pathname === '/login') {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    );
  }

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="spin" style={{ fontSize: 28, color: 'var(--accent)' }}>⟳</div>
      </div>
    );
  }

  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ORGANIZER') {
    return (
      <div style={{ padding: 28 }}>
        <h1>Acceso restringido</h1>
        <p>Este panel es solo para organizadores. Tu rol: {user.role}.</p>
      </div>
    );
  }

  return (
    <Shell
      subtitle="organizer workspace"
      items={buildNav(location.pathname, navigate)}
      user={{ name: user.email.split('@')[0], role: user.role, email: user.email }}
      onLogout={() => logout().then(() => navigate('/login'))}
    >
      <NotificationBell />
      <Routes>
        <Route path="/" element={<OrganizerPortalPage />} />
        <Route path="/tournaments" element={<OrganizerTournamentsPage />} />
        <Route path="/tournaments/:id" element={<TournamentDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
};
