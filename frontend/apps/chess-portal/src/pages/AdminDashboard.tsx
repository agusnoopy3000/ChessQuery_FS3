import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { adminApi } from '../api';
import { circuitStateVariant, etlSources, formatNumber } from '../portal-utils';

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
    const etlStatus = asRecord(asRecord(dashboard.data.etl).status);
    const breakers = asRecord(etlStatus.circuitBreakers);
    const breakerEntries = Object.entries(breakers);
    const healthy = breakerEntries.filter(([, value]) => asRecord(value).state === 'CLOSED').length;

    return {
      users: asNumber(asRecord(dashboard.data.users).total ?? platform.totalPlayers),
      tournaments: asNumber(platform.activeTournaments),
      games: asNumber(platform.totalGames),
      sourcesHealthy: healthy,
      sourcesTotal: breakerEntries.length,
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

  const breakers = asRecord(asRecord(asRecord(dashboard.data.etl).status).circuitBreakers);

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Panel de Administración</div>
          <h1 className="page-title">Visión global de ChessQuery y estado de sus fuentes.</h1>
          <p className="page-copy">
            Tomé como referencia el lenguaje visual de tus capturas: cards anchas, indicadores con contraste fuerte,
            lecturas rápidas y un bloque ETL más operativo para saltar de inmediato a la revisión de fuentes.
          </p>
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
        <Card className="metric-card info-card">
          <div className="metric-label">Torneos activos</div>
          <div className="metric-value">{formatNumber(metrics.tournaments)}</div>
        </Card>
        <Card className="metric-card gold-card">
          <div className="metric-label">Partidas jugadas</div>
          <div className="metric-value">{formatNumber(metrics.games)}</div>
        </Card>
        <Card className="metric-card subtle-card">
          <div className="metric-label">Fuentes saludables</div>
          <div className="metric-value">
            {metrics.sourcesHealthy}/{metrics.sourcesTotal}
          </div>
        </Card>
      </div>

      <div className="panel-grid" style={{ gridTemplateColumns: '1.25fr 0.75fr' }}>
        <Card header="Resumen ETL">
          <div style={{ display: 'grid', gap: 12 }}>
            {etlSources.map((source) => {
              const breaker = asRecord(breakers[source.key]);
              const state = String(breaker.state ?? 'UNKNOWN');

              return (
                <div key={source.key} className="status-row">
                  <div>
                    <div style={{ fontWeight: 700 }}>{source.icon} {source.label}</div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>{source.description}</div>
                  </div>
                  <Badge variant={circuitStateVariant(state)} dot>
                    {state}
                  </Badge>
                </div>
              );
            })}
          </div>
        </Card>

        <Card header="Actividad reciente">
          <div style={{ display: 'grid', gap: 10 }}>
            {etlSources.map((source) => {
              const breaker = asRecord(breakers[source.key]);
              const state = String(breaker.state ?? 'UNKNOWN');

              return (
                <div key={source.key} className="activity-row">
                  <div className="activity-dot" data-state={state} />
                  <div>
                    <div style={{ fontWeight: 600 }}>
                      {source.label} · {state}
                    </div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      Failure count: {asNumber(breaker.failureCount)} / {asNumber(breaker.failureThreshold)}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      </div>
    </div>
  );
};
