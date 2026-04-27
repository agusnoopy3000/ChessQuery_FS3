import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Role, useAuth } from '@chessquery/shared';
import { Button, Shell, ShellNavItem } from '@chessquery/ui-lib';
import { getDefaultRoute } from './portal-utils';
import { AdminDashboardPage } from './pages/AdminDashboard';
import { AdminEtlPage } from './pages/AdminEtl';
import { HomePage } from './pages/Home';
import { LoginPage } from './pages/Login';
import { MyDashboardPage } from './pages/MyDashboard';
import { OrganizerPlayersPage } from './pages/OrganizerPlayers';
import { OrganizerPortalPage } from './pages/OrganizerPortal';
import { OrganizerTournamentsPage } from './pages/OrganizerTournaments';
import { PlayerMatchmakingPage } from './pages/PlayerMatchmaking';
import { PlayerPortalPage } from './pages/PlayerPortal';
import { PlayerProfilePage } from './pages/PlayerProfile';
import { RankingsPage } from './pages/Rankings';
import { RegisterPage } from './pages/Register';
import { SearchPage } from './pages/Search';
import { TournamentDetailPage } from './pages/TournamentDetail';
import { TournamentsPage } from './pages/Tournaments';

interface RequireRoleProps {
  userRole?: Role;
  roles: Role[];
  children: JSX.Element;
}

const RequireRole = ({ userRole, roles, children }: RequireRoleProps) => {
  if (!userRole) {
    return <Navigate to="/login" replace />;
  }

  if (!roles.includes(userRole)) {
    return <Navigate to={getDefaultRoute(userRole)} replace />;
  }

  return children;
};

const buildNavItems = (
  role: Role | undefined,
  pathname: string,
  navigate: ReturnType<typeof useNavigate>,
): ShellNavItem[] => {
  if (role === 'ORGANIZER') {
    return [
      { id: 'organizer-home', label: 'Portal', icon: '♖', desc: 'Resumen del organizador', active: pathname === '/organizer', onClick: () => navigate('/organizer') },
      { id: 'organizer-players', label: 'Validar jugadores', icon: '♟', desc: 'Perfiles y seedings', active: pathname.startsWith('/organizer/players'), onClick: () => navigate('/organizer/players') },
      { id: 'organizer-tournaments', label: 'Gestión torneos', icon: '♜', desc: 'Estado, rondas y standings', active: pathname.startsWith('/organizer/tournaments'), onClick: () => navigate('/organizer/tournaments') },
      { id: 'rankings', label: 'Ranking', icon: '♕', desc: 'Consulta nacional', active: pathname.startsWith('/rankings'), onClick: () => navigate('/rankings') },
      { id: 'search', label: 'Buscar', icon: '⌕', desc: 'Consulta de jugadores', active: pathname.startsWith('/search'), onClick: () => navigate('/search') },
    ];
  }

  if (role === 'ADMIN') {
    return [
      { id: 'admin-home', label: 'Dashboard', icon: '♔', desc: 'Vista global', active: pathname === '/admin', onClick: () => navigate('/admin') },
      { id: 'admin-etl', label: 'ETL / Fuentes', icon: '⟳', desc: 'Circuit breakers y sync', active: pathname.startsWith('/admin/etl'), onClick: () => navigate('/admin/etl') },
      { id: 'rankings', label: 'Ranking', icon: '♕', desc: 'Referencia pública', active: pathname.startsWith('/rankings'), onClick: () => navigate('/rankings') },
      { id: 'tournaments', label: 'Torneos', icon: '♜', desc: 'Competencias activas', active: pathname.startsWith('/tournaments'), onClick: () => navigate('/tournaments') },
    ];
  }

  if (role === 'PLAYER') {
    return [
      { id: 'portal', label: 'Portal', icon: '♔', desc: 'Centro del jugador', active: pathname === '/portal', onClick: () => navigate('/portal') },
      { id: 'play', label: 'Jugar', icon: '♞', desc: 'Emparejamientos', active: pathname.startsWith('/play'), onClick: () => navigate('/play') },
      { id: 'search', label: 'Jugadores', icon: '⌕', desc: 'Consulta perfiles', active: pathname.startsWith('/search'), onClick: () => navigate('/search') },
      { id: 'rankings', label: 'Ranking', icon: '♕', desc: 'Top 10 nacional', active: pathname.startsWith('/rankings'), onClick: () => navigate('/rankings') },
      { id: 'tournaments', label: 'Torneos', icon: '♜', desc: 'Competencias activas', active: pathname.startsWith('/tournaments'), onClick: () => navigate('/tournaments') },
      { id: 'me', label: 'Mi progreso', icon: '♙', desc: 'Dashboard personal', active: pathname === '/player/me', onClick: () => navigate('/player/me') },
    ];
  }

  return [
    { id: 'home', label: 'Inicio', icon: '♔', desc: 'Landing pública', active: pathname === '/', onClick: () => navigate('/') },
    { id: 'rankings', label: 'Ranking', icon: '♕', desc: 'Top nacional', active: pathname.startsWith('/rankings'), onClick: () => navigate('/rankings') },
    { id: 'tournaments', label: 'Torneos', icon: '♜', desc: 'Competencias', active: pathname.startsWith('/tournaments'), onClick: () => navigate('/tournaments') },
    { id: 'search', label: 'Buscar', icon: '⌕', desc: 'Encuentra jugadores', active: pathname.startsWith('/search'), onClick: () => navigate('/search') },
  ];
};

export const App = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, loading } = useAuth();

  if (location.pathname === '/login' || location.pathname === '/register') {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
      </Routes>
    );
  }

  if (loading) {
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div className="spin" style={{ fontSize: 28, color: 'var(--accent)' }}>
          ⟳
        </div>
      </div>
    );
  }

  const items = buildNavItems(user?.role, location.pathname, navigate);

  return (
    <Shell
      subtitle={user?.role ? `${user.role.toLowerCase()} workspace` : 'portal público'}
      items={items}
      user={user ? { name: user.email.split('@')[0], role: user.role, email: user.email } : undefined}
      onLogout={user ? () => logout().then(() => navigate('/')) : undefined}
    >
      {!user ? (
        <div className="public-topbar">
          <Button variant="ghost" onClick={() => navigate('/register')}>
            Crear cuenta
          </Button>
          <Button onClick={() => navigate('/login')}>Iniciar sesión</Button>
        </div>
      ) : null}

      <Routes>
        <Route path="/" element={user ? <Navigate to={getDefaultRoute(user.role)} replace /> : <HomePage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/rankings" element={<RankingsPage />} />
        <Route path="/tournaments" element={<TournamentsPage />} />
        <Route path="/tournaments/:id" element={<TournamentDetailPage />} />
        <Route path="/player/:id" element={<PlayerProfilePage />} />

        <Route
          path="/portal"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <PlayerPortalPage />
            </RequireRole>
          }
        />
        <Route
          path="/play"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <PlayerMatchmakingPage />
            </RequireRole>
          }
        />
        <Route
          path="/player/me"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <MyDashboardPage />
            </RequireRole>
          }
        />

        <Route
          path="/organizer"
          element={
            <RequireRole userRole={user?.role} roles={['ORGANIZER']}>
              <OrganizerPortalPage />
            </RequireRole>
          }
        />
        <Route
          path="/organizer/players"
          element={
            <RequireRole userRole={user?.role} roles={['ORGANIZER']}>
              <OrganizerPlayersPage />
            </RequireRole>
          }
        />
        <Route
          path="/organizer/tournaments"
          element={
            <RequireRole userRole={user?.role} roles={['ORGANIZER']}>
              <OrganizerTournamentsPage />
            </RequireRole>
          }
        />

        <Route
          path="/admin"
          element={
            <RequireRole userRole={user?.role} roles={['ADMIN']}>
              <AdminDashboardPage />
            </RequireRole>
          }
        />
        <Route
          path="/admin/etl"
          element={
            <RequireRole userRole={user?.role} roles={['ADMIN']}>
              <AdminEtlPage />
            </RequireRole>
          }
        />

        <Route path="*" element={<Navigate to={user ? getDefaultRoute(user.role) : '/'} replace />} />
      </Routes>
    </Shell>
  );
};
