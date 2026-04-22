import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';
import {
  Card,
  Badge,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Table,
  TableColumn,
  Select,
} from '@chessquery/ui-lib';
import { Game } from '@chessquery/shared';
import { playerApi } from '../api';

const RATING_TYPES = [
  { value: 'FIDE_STANDARD', label: 'FIDE Clásico' },
  { value: 'FIDE_RAPID', label: 'FIDE Rápido' },
  { value: 'FIDE_BLITZ', label: 'FIDE Blitz' },
  { value: 'NATIONAL', label: 'Nacional' },
];

export const MyDashboardPage = () => {
  const navigate = useNavigate();
  const [ratingType, setRatingType] = useState('FIDE_STANDARD');
  const [months, setMonths] = useState(12);

  const dashboard = useQuery({
    queryKey: ['me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const chart = useQuery({
    queryKey: ['me', 'rating-chart', ratingType, months],
    queryFn: () => playerApi.ratingChart(ratingType, months),
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

  const { profile: p, stats, recentGames } = dashboard.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Jugador';

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Bienvenido de vuelta</div>
        <h1 style={{ fontSize: 26, fontWeight: 700 }}>{fullName}</h1>
      </div>

      {/* Tarjetas de ELO */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        {p.eloFideStandard != null && (
          <Card header="FIDE Clásico">
            <RatingBadge rating={p.eloFideStandard} />
          </Card>
        )}
        {p.eloFideRapid != null && (
          <Card header="FIDE Rápido">
            <RatingBadge rating={p.eloFideRapid} />
          </Card>
        )}
        {p.eloFideBlitz != null && (
          <Card header="FIDE Blitz">
            <RatingBadge rating={p.eloFideBlitz} />
          </Card>
        )}
        {p.eloNational != null && (
          <Card header="Rating Nacional">
            <RatingBadge rating={p.eloNational} />
          </Card>
        )}
      </div>

      {/* Estadísticas */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 12 }}>
        <Card header="Partidas"><div style={{ fontSize: 26, fontWeight: 700 }}>{stats?.totalGames ?? 0}</div></Card>
        <Card header="Victorias"><div style={{ fontSize: 26, fontWeight: 700, color: 'var(--accent)' }}>{stats?.wins ?? 0}</div></Card>
        <Card header="Tablas"><div style={{ fontSize: 26, fontWeight: 700 }}>{stats?.draws ?? 0}</div></Card>
        <Card header="Derrotas"><div style={{ fontSize: 26, fontWeight: 700, color: 'var(--red)' }}>{stats?.losses ?? 0}</div></Card>
        <Card header="% Victoria">
          <div style={{ fontSize: 26, fontWeight: 700 }}>
            {stats?.winRate != null ? `${(stats.winRate * 100).toFixed(1)}%` : '—'}
          </div>
        </Card>
      </div>

      {/* Grafico de rating */}
      <Card
        header={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <span>Evolución de rating</span>
            <div style={{ display: 'flex', gap: 8 }}>
              <Select
                value={ratingType}
                onChange={(e) => setRatingType(e.target.value)}
                options={RATING_TYPES}
                style={{ width: 160 }}
              />
              <Select
                value={String(months)}
                onChange={(e) => setMonths(Number(e.target.value))}
                options={[
                  { value: '3', label: 'Últimos 3 meses' },
                  { value: '6', label: 'Últimos 6 meses' },
                  { value: '12', label: 'Último año' },
                  { value: '24', label: 'Últimos 2 años' },
                ]}
                style={{ width: 170 }}
              />
            </div>
          </div>
        }
      >
        {chart.isLoading ? (
          <Skeleton height={280} />
        ) : chart.isError ? (
          <ErrorAlert message="No se pudo cargar el gráfico" onRetry={() => chart.refetch()} />
        ) : !chart.data || chart.data.length === 0 ? (
          <EmptyState title="Sin datos" description="Aún no tienes historial de rating para este tipo" />
        ) : (
          <div style={{ width: '100%', height: 300 }}>
            <ResponsiveContainer>
              <LineChart data={chart.data}>
                <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" />
                <XAxis dataKey="date" stroke="var(--text-muted)" fontSize={11} />
                <YAxis stroke="var(--text-muted)" fontSize={11} domain={['dataMin - 40', 'dataMax + 40']} />
                <Tooltip
                  contentStyle={{
                    background: 'var(--surface)',
                    border: '1px solid var(--border)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                />
                <Line type="monotone" dataKey="rating" stroke="oklch(68% 0.20 150)" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

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
