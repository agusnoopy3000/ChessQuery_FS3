import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Shell, ShellNavItem } from '@chessquery/ui-lib';
import { OrganizerLoginPage } from './pages/Login';
import { OrganizerDashboardPage } from './pages/Dashboard';
import { CreateTournamentPage } from './pages/CreateTournament';
import { ManageTournamentPage } from './pages/ManageTournament';

export const App = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, loading } = useAuth();

  if (location.pathname === '/login') {
    return (
      <Routes>
        <Route path="/login" element={<OrganizerLoginPage />} />
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
  if (user.role !== 'ORGANIZER' && user.role !== 'ADMIN') {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <h2>Acceso denegado</h2>
        <p>Tu cuenta no tiene permisos de organizador.</p>
        <button className="btn btn-ghost" onClick={() => logout()}>
          Cerrar sesión
        </button>
      </div>
    );
  }

  const items: ShellNavItem[] = [
    {
      id: 'dashboard',
      label: 'Dashboard',
      icon: '♞',
      desc: 'Mis torneos',
      active: location.pathname === '/',
      onClick: () => navigate('/'),
    },
    {
      id: 'create',
      label: 'Crear torneo',
      icon: '+',
      desc: 'Nuevo evento',
      active: location.pathname === '/tournaments/new',
      onClick: () => navigate('/tournaments/new'),
    },
  ];

  return (
    <Shell
      subtitle="Organizador"
      items={items}
      user={{ name: user.email.split('@')[0], role: user.role, email: user.email }}
      onLogout={() => logout().then(() => navigate('/login'))}
    >
      <Routes>
        <Route path="/" element={<OrganizerDashboardPage />} />
        <Route path="/tournaments/new" element={<CreateTournamentPage />} />
        <Route path="/tournaments/:id" element={<ManageTournamentPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
};
