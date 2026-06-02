import { useEffect } from 'react';
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
import { ForgotPasswordPage } from './pages/ForgotPassword';
import { ResetPasswordPage } from './pages/ResetPassword';
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

export const organizerPanelUrl = (): string =>
  `${window.location.protocol}//${window.location.hostname}:5174`;

/**
 * Cuando un ORGANIZER aterriza en el portal de jugadores (por login,
 * registro o entrando a la URL directamente), lo redirigimos automaticamente
 * al organizer-panel en puerto 5174 sin pasos manuales.
 *
 * Mientras la redireccion ocurre (~150ms) mostramos un spinner para evitar
 * un flash de UI vacía o engañosa.
 */
const OrganizerRedirect = () => {
  useEffect(() => {
    window.location.assign(organizerPanelUrl());
  }, []);
  return (
    <div style={{ padding: 60, display: 'flex', justifyContent: 'center' }}>
      <Card style={{ maxWidth: 480, padding: 32, textAlign: 'center' }}>
        <div className="spin" style={{ fontSize: 36, color: 'var(--accent)', marginBottom: 14 }}>⟳</div>
        <h2 style={{ marginTop: 0 }}>Llevándote al panel del organizador…</h2>
        <p style={{ color: 'var(--text-muted)', marginBottom: 16, fontSize: 13 }}>
          Si la redirección no ocurre automáticamente, haz click abajo.
        </p>
        <Button size="md" onClick={() => window.location.assign(organizerPanelUrl())}>
          Abrir panel ahora →
        </Button>
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

  const authRoutes = ['/login', '/register', '/forgot-password', '/reset-password'];
  if (authRoutes.includes(location.pathname)) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
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
      subtitle={user?.role === 'PLAYER' ? 'Tu portal de jugador' : user?.role === 'ORGANIZER' ? 'Tu panel del organizador' : 'Bienvenido a ChessQuery'}
      items={items}
      user={user ? { name: (user.name && user.name.trim()) || user.email.split('@')[0], role: user.role, email: user.email } : undefined}
      onLogout={user ? () => logout().then(() => navigate('/')) : undefined}
    >
      {user?.role === 'PLAYER' && <NotificationBell />}

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
