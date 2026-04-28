import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Badge,
  RatingBadge,
  Skeleton,
  ErrorAlert,
} from '@chessquery/ui-lib';
import { playerApi } from '../api';

export const MyDashboardPage = () => {
  const dashboard = useQuery({
    queryKey: ['me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={100} />
        <Skeleton height={320} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert
          title="No se pudo cargar tu dashboard"
          onRetry={() => dashboard.refetch()}
        />
      </div>
    );
  }

  const { profile: p } = dashboard.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Jugador';
  const clubLabel = p.clubName ?? p.countryName ?? '—';
  const birthDate = p.birthDate ?? '—';

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Bienvenido de vuelta</div>
        <h1 style={{ fontSize: 26, fontWeight: 700 }}>{fullName}</h1>
      </div>

      {/* Datos personales */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
        <Card header="Nombre completo">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{fullName}</div>
        </Card>
        <Card header="Club">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{clubLabel}</div>
        </Card>
        <Card header="Fecha de nacimiento">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{birthDate}</div>
        </Card>
        <Card header="ELO FIDE">
          {p.eloFideStandard != null ? <RatingBadge rating={p.eloFideStandard} /> : <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>}
        </Card>
        <Card header="ELO Nacional">
          {p.eloNational != null ? <RatingBadge rating={p.eloNational} /> : <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>}
        </Card>
        {p.enrichmentSource && (
          <Card header="Datos federados">
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              <Badge variant="gold">{p.enrichmentSource}</Badge>
              {p.federationId && (
                <span style={{ fontSize: 13, color: 'var(--text-muted)' }}>#{p.federationId}</span>
              )}
            </div>
          </Card>
        )}
      </div>

    </div>
  );
};
