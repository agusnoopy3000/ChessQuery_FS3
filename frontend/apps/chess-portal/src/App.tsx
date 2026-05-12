import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Role, useAuth } from '@chessquery/shared';
import { Button, Card, Shell, ShellNavItem } from '@chessquery/ui-lib';
import { NotificationBell } from './components/NotificationBell';
import { getDefaultRoute } from './portal-utils';
import { HomePage } from './pages/Home';
import { LoginPage } from './pages/Login';
import { MyDashboardPage } from './pages/MyDashboard';
import { PlayerMatchmakingPage } from './pages/PlayerMatchmaking';
import { LiveGamePage } from './pages/LiveGame';
import { PlayerPortalPage } from './pages/PlayerPortal';
import { RegisterPage } from './pages/Register';
import { TournamentDetailPage } from './pages/TournamentDetail';
import { TournamentsPage } from './pages/Tournaments';

interface RequireRoleProps {
  userRole?: Role;
  roles: Role[];
  children: JSX.Element;
}

const RequireRole = ({ userRole, roles, children }: RequireRoleProps) => {
  const location = useLocation();

  if (!userRole) {
    // Preservamos la URL original (ej: /play/123) para volver post-login
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  if (!roles.includes(userRole)) {
    return <Navigate to={getDefaultRoute(userRole)} replace />;
  }

  return children;
};

/**
 * Mensaje cuando un ORGANIZER intenta entrar al portal de jugadores.
 * El organizer-panel es una app separada en otro puerto.
 */
const OrganizerRedirect = () => {
  const organizerUrl = `${window.location.protocol}//${window.location.hostname}:5174`;
  return (
    <div style={{ padding: 60, display: 'flex', justifyContent: 'center' }}>
      <Card style={{ maxWidth: 520, padding: 32, textAlign: 'center' }}>
        <div style={{ fontSize: 48, marginBottom: 16 }}>♖</div>
        <h2 style={{ marginTop: 0 }}>Bienvenido, organizador</h2>
        <p style={{ color: 'var(--text-muted)', marginBottom: 24 }}>
          Este es el portal de jugadores. Como organizador, gestionas torneos
          desde el <strong>Panel del Organizador</strong> en una URL distinta.
        </p>
        <Button size="lg" onClick={() => window.location.assign(organizerUrl)}>
          Ir al Panel del Organizador →
        </Button>
        <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 16 }}>
          ({organizerUrl})
        </p>
      </Card>
    </div>
  );
};

const buildNavItems = (
  role: Role | undefined,
  pathname: string,
  navigate: ReturnType<typeof useNavigate>,
): ShellNavItem[] => {
  if (role === 'PLAYER') {
    // startsWith('/play') matchea tanto '/play' como '/player/me' por
    // prefijo de string; usamos comparación exacta + '/' separador para
    // evitar que dos items aparezcan resaltados simultaneamente.
    const isPlay = pathname === '/play' || pathname.startsWith('/play/');
    const isTournaments = pathname === '/tournaments' || pathname.startsWith('/tournaments/');
    const isMe = pathname === '/player/me' || pathname.startsWith('/player/');
    return [
      { id: 'portal', label: 'Portal', icon: '♔', desc: 'Centro del jugador', active: pathname === '/portal', onClick: () => navigate('/portal') },
      { id: 'play', label: 'Jugar', icon: '♞', desc: 'Emparejamientos', active: isPlay, onClick: () => navigate('/play') },
      { id: 'tournaments', label: 'Torneos', icon: '♜', desc: 'Inscripción a torneos', active: isTournaments, onClick: () => navigate('/tournaments') },
      { id: 'me', label: 'Mi perfil', icon: '♙', desc: 'Estadísticas y cuenta', active: isMe, onClick: () => navigate('/player/me') },
    ];
  }

  // Visitante (sin login): solo landing pública mínima
  return [
    { id: 'home', label: 'Inicio', icon: '♔', desc: 'Landing pública', active: pathname === '/', onClick: () => navigate('/') },
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
      user={user ? { name: (user.name && user.name.trim()) || user.email.split('@')[0], role: user.role, email: user.email } : undefined}
      onLogout={user ? () => logout().then(() => navigate('/')) : undefined}
    >
      {!user ? (
        <div className="public-topbar">
          <Button variant="ghost" onClick={() => navigate('/register')}>
            Crear cuenta
          </Button>
          <Button onClick={() => navigate('/login')}>Iniciar sesión</Button>
        </div>
      ) : user.role === 'PLAYER' ? (
        <NotificationBell />
      ) : null}

      <Routes>
        <Route
          path="/"
          element={
            !user
              ? <HomePage />
              : user.role === 'ORGANIZER'
                ? <OrganizerRedirect />
                : <Navigate to={getDefaultRoute(user.role)} replace />
          }
        />

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
          path="/play/:id"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <LiveGamePage />
            </RequireRole>
          }
        />
        <Route
          path="/tournaments"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <TournamentsPage />
            </RequireRole>
          }
        />
        <Route
          path="/tournaments/:id"
          element={
            <RequireRole userRole={user?.role} roles={['PLAYER']}>
              <TournamentDetailPage />
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

        <Route path="*" element={<Navigate to={user ? getDefaultRoute(user.role) : '/'} replace />} />
      </Routes>
    </Shell>
  );
};
