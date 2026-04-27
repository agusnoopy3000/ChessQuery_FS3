import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Badge,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Table,
  TableColumn,
} from '@chessquery/ui-lib';
import { Game } from '@chessquery/shared';
import { playerApi } from '../api';

export const MyDashboardPage = () => {
  const navigate = useNavigate();

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

  const { profile: p, recentGames } = dashboard.data;
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

      {/* Partidas recientes */}
      <Card header="Partidas recientes">
        {!recentGames || recentGames.length === 0 ? (
          <EmptyState title="Aún no has jugado" description="Inscríbete en un torneo para comenzar" />
        ) : (
          <Table<Game>
            rows={recentGames}
            rowKey={(r) => r.id}
            columns={[
              { key: 'date', header: 'Fecha', render: (r) => r.playedAt?.slice(0, 10) ?? '—' },
              {
                key: 'opp',
                header: 'Oponente',
                render: (r) => {
                  const isWhite = r.whitePlayerId === p.id;
                  const oppName = isWhite ? r.blackName : r.whiteName;
                  const oppId = isWhite ? r.blackPlayerId : r.whitePlayerId;
                  return (
                    <button
                      className="btn btn-ghost"
                      onClick={() => navigate(`/player/${oppId}`)}
                      style={{ padding: '2px 6px', fontSize: 13 }}
                    >
                      {oppName ?? `#${oppId}`}
                    </button>
                  );
                },
              },
              { key: 'color', header: 'Color', align: 'center', render: (r) => (r.whitePlayerId === p.id ? '♔' : '♚') },
              { key: 'result', header: 'Resultado', align: 'center', render: (r) => <Badge>{r.result}</Badge> },
              { key: 'opening', header: 'Apertura', render: (r) => r.openingName ?? '—' },
            ] as TableColumn<Game>[]}
          />
        )}
      </Card>
    </div>
  );
};
