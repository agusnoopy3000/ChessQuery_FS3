import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Shell, ShellNavItem } from '@chessquery/ui-lib';

import { LoginPage } from '../../chess-portal/src/pages/Login';
import { OrganizerPortalPage } from '../../chess-portal/src/pages/OrganizerPortal';
import { OrganizerPlayersPage } from '../../chess-portal/src/pages/OrganizerPlayers';
import { OrganizerTournamentsPage } from '../../chess-portal/src/pages/OrganizerTournaments';
import { TournamentDetailPage } from '../../chess-portal/src/pages/TournamentDetail';
import { RankingsPage } from '../../chess-portal/src/pages/Rankings';
import { SearchPage } from '../../chess-portal/src/pages/Search';
import { PlayerProfilePage } from '../../chess-portal/src/pages/PlayerProfile';

const buildNav = (pathname: string, navigate: ReturnType<typeof useNavigate>): ShellNavItem[] => [
  { id: 'home', label: 'Portal', icon: '♖', desc: 'Resumen del organizador', active: pathname === '/', onClick: () => navigate('/') },
  { id: 'players', label: 'Validar jugadores', icon: '♟', desc: 'Perfiles y seedings', active: pathname.startsWith('/players'), onClick: () => navigate('/players') },
  { id: 'tournaments', label: 'Gestión torneos', icon: '♜', desc: 'Estado, rondas y standings', active: pathname.startsWith('/tournaments'), onClick: () => navigate('/tournaments') },
  { id: 'rankings', label: 'Ranking', icon: '♕', desc: 'Consulta nacional', active: pathname.startsWith('/rankings'), onClick: () => navigate('/rankings') },
  { id: 'search', label: 'Buscar', icon: '⌕', desc: 'Consulta de jugadores', active: pathname.startsWith('/search'), onClick: () => navigate('/search') },
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
      <Routes>
        <Route path="/" element={<OrganizerPortalPage />} />
        <Route path="/players" element={<OrganizerPlayersPage />} />
        <Route path="/tournaments" element={<OrganizerTournamentsPage />} />
        <Route path="/tournaments/:id" element={<TournamentDetailPage />} />
        <Route path="/rankings" element={<RankingsPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/player/:id" element={<PlayerProfilePage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
};
