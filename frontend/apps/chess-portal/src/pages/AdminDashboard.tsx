import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { adminApi } from '../api';
import { formatNumber } from '../portal-utils';

const asRecord = (value: unknown): Record<string, unknown> =>
  value && typeof value === 'object' ? (value as Record<string, unknown>) : {};

const asNumber = (value: unknown) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim().length > 0) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return 0;
};

export const AdminDashboardPage = () => {
  const navigate = useNavigate();
  const dashboard = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: () => adminApi.dashboard(),
  });

  const metrics = useMemo(() => {
    if (!dashboard.data) return null;

    const platform = asRecord(asRecord(dashboard.data.analytics).platform);
    const etlStatus = asRecord(dashboard.data.etl).status;
    const breakerList = Array.isArray(etlStatus) ? etlStatus : [];
    const healthy = breakerList.filter((item) => asRecord(item).circuitBreakerState === 'CLOSED').length;

    const tournamentsActive = asRecord(asRecord(dashboard.data.tournaments).active);
    const gamesRecent = asRecord(asRecord(dashboard.data.games).recent);

    return {
      users: asNumber(asRecord(dashboard.data.users).total ?? platform.totalPlayers),
      tournaments: asNumber(platform.activeTournaments ?? tournamentsActive.totalElements),
      games: asNumber(platform.totalGames ?? gamesRecent.totalElements),
      sourcesHealthy: healthy,
      sourcesTotal: breakerList.length,
    };
  }, [dashboard.data]);

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16 }}>
        <Skeleton height={160} />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14 }}>
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={index} height={160} />
          ))}
        </div>
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data || !metrics) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="No se pudo cargar el dashboard admin" onRetry={() => dashboard.refetch()} />
      </div>
    );
  }

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Panel de Administración</div>
          <h1 className="page-title">Visión global de ChessQuery y estado de sus fuentes.</h1>
        </div>
        <Button size="lg" onClick={() => navigate('/admin/etl')}>
          Ir a ETL / Fuentes
        </Button>
      </section>

      <div className="admin-metrics-grid">
        <Card className="metric-card accent-card">
          <div className="metric-label">Jugadores registrados</div>
          <div className="metric-value">{formatNumber(metrics.users)}</div>
        </Card>
        <Card className="metric-card gold-card">
          <div className="metric-label">Partidas jugadas</div>
          <div className="metric-value">{formatNumber(metrics.games)}</div>
        </Card>
        <Card className="metric-card info-card">
          <div className="metric-label">Torneos activos</div>
          <div className="metric-value">{formatNumber(metrics.tournaments)}</div>
        </Card>
        <Card className="metric-card subtle-card">
          <div className="metric-label">Fuentes</div>
          <div className="metric-value">
            {metrics.sourcesHealthy}/{metrics.sourcesTotal}
          </div>
        </Card>
      </div>
    </div>
  );
};
