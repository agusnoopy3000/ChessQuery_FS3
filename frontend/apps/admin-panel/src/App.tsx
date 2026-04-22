import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@chessquery/shared';
import { Shell, ShellNavItem } from '@chessquery/ui-lib';
import { AdminLoginPage } from './pages/Login';
import { AdminDashboardPage } from './pages/Dashboard';
import { EtlPage } from './pages/Etl';
import { UsersPage } from './pages/Users';

export const App = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, loading } = useAuth();

  if (location.pathname === '/login') {
    return (
      <Routes>
        <Route path="/login" element={<AdminLoginPage />} />
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
  if (user.role !== 'ADMIN') {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <h2>Acceso denegado</h2>
        <p>Esta área está reservada para administradores.</p>
        <button className="btn btn-ghost" onClick={() => logout()}>Cerrar sesión</button>
      </div>
    );
  }

  const items: ShellNavItem[] = [
    { id: 'dash', label: 'Dashboard', icon: '♕', desc: 'Resumen general', active: location.pathname === '/', onClick: () => navigate('/') },
    { id: 'etl', label: 'ETL', icon: '⟳', desc: 'Sincronización', active: location.pathname.startsWith('/etl'), onClick: () => navigate('/etl') },
    { id: 'users', label: 'Usuarios', icon: '♟', desc: 'Gestión de cuentas', active: location.pathname.startsWith('/users'), onClick: () => navigate('/users') },
  ];

  return (
    <Shell
      subtitle="Administración"
      items={items}
      user={{ name: user.email.split('@')[0], role: user.role, email: user.email }}
      onLogout={() => logout().then(() => navigate('/login'))}
    >
      <Routes>
        <Route path="/" element={<AdminDashboardPage />} />
        <Route path="/etl" element={<EtlPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  );
};
