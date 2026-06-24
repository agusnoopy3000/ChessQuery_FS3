import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Shell, ShellNavItem } from '@chessquery/ui-lib';

import { LoginPage } from './pages/Login';

// Vistas internas lazy → bundle inicial más liviano (la login carga primero).
const NotificationBell = lazy(() => import('./components/NotificationBell').then((m) => ({ default: m.NotificationBell })));
const OrganizerPortalPage = lazy(() => import('./pages/OrganizerPortal').then((m) => ({ default: m.OrganizerPortalPage })));
const OrganizerTournamentsPage = lazy(() => import('./pages/OrganizerTournaments').then((m) => ({ default: m.OrganizerTournamentsPage })));
const TournamentDetailPage = lazy(() => import('./pages/TournamentDetail').then((m) => ({ default: m.TournamentDetailPage })));

const RouteFallback = () => (
  <div style={{ minHeight: '60vh', display: 'grid', placeItems: 'center' }}>
    <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
      <div className="spin" style={{ fontSize: 30, color: 'var(--accent)' }}>⟳</div>
      <div style={{ marginTop: 12, fontSize: 13 }}>Cargando…</div>
    </div>
  </div>
);

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
      subtitle="Tu panel del organizador"
      items={buildNav(location.pathname, navigate)}
      user={{ name: (user.name && user.name.trim()) || user.email.split('@')[0], role: user.role, email: user.email }}
      onLogout={() => {
        // Refrescar la bandeja: al cerrar sesión, la próxima arranca vacía.
        try { sessionStorage.removeItem('cq-notif-baseline'); } catch { /* ignore */ }
        logout().then(() => navigate('/login'));
      }}
    >
      <Suspense fallback={null}><NotificationBell /></Suspense>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/" element={<OrganizerPortalPage />} />
          <Route path="/tournaments" element={<OrganizerTournamentsPage />} />
          <Route path="/tournaments/:id" element={<TournamentDetailPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </Shell>
  );
};
