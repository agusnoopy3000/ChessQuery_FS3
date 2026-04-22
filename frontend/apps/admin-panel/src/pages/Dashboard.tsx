import { useQuery } from '@tanstack/react-query';
import { Card, Skeleton, ErrorAlert, Badge } from '@chessquery/ui-lib';
import { adminApi } from '../api';

const formatValue = (v: unknown): string => {
  if (v == null) return '—';
  if (typeof v === 'number') return v.toLocaleString('es-CL');
  if (typeof v === 'string') return v;
  if (Array.isArray(v)) return String(v.length);
  if (typeof v === 'object') {
    const obj = v as Record<string, unknown>;
    if (typeof obj.total === 'number') return obj.total.toLocaleString('es-CL');
    if (typeof obj.count === 'number') return obj.count.toLocaleString('es-CL');
  }
  return JSON.stringify(v);
};

export const AdminDashboardPage = () => {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => adminApi.dashboard(),
  });

  if (isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} height={80} />)}
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="No se pudo cargar el dashboard" onRetry={() => refetch()} />
      </div>
    );
  }

  const sections = [
    { key: 'users', label: 'Usuarios', icon: '♟', value: formatValue(data.users?.total), error: data.users?.error },
    { key: 'tournaments', label: 'Torneos activos', icon: '♞', value: formatValue(data.tournaments?.active), error: data.tournaments?.error },
    { key: 'games', label: 'Partidas recientes', icon: '♔', value: formatValue(data.games?.recent), error: data.games?.error },
    { key: 'analytics', label: 'Analytics', icon: '♕', value: formatValue(data.analytics?.platform), error: data.analytics?.error },
  ];

  const cbs = (data.etl?.status as { circuitBreakers?: Record<string, { state: string }> } | null)?.circuitBreakers;

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <h1 style={{ fontSize: 24, fontWeight: 700 }}>Dashboard</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Vista general de la plataforma</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
        {sections.map((s) => (
          <Card key={s.key} header={<span>{s.icon} {s.label}</span>}>
            {s.error ? (
              <div style={{ color: 'var(--red)', fontSize: 12 }}>⚠ {s.error}</div>
            ) : (
              <div style={{ fontSize: 32, fontWeight: 700 }}>{s.value}</div>
            )}
          </Card>
        ))}
      </div>

      <Card header="Estado de Circuit Breakers (ETL)">
        {data.etl?.error ? (
          <div style={{ color: 'var(--red)', fontSize: 13 }}>⚠ {data.etl.error}</div>
        ) : !cbs || Object.keys(cbs).length === 0 ? (
          <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>Sin circuit breakers registrados</div>
        ) : (
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {Object.entries(cbs).map(([name, s]) => (
              <Badge
                key={name}
                variant={s.state === 'CLOSED' ? 'success' : s.state === 'HALF_OPEN' ? 'warning' : 'danger'}
              >
                {name}: {s.state}
              </Badge>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};
