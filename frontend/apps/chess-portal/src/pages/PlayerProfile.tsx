import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
} from '@chessquery/ui-lib';
import { Game, RatingHistoryPoint } from '@chessquery/shared';
import { playerApi } from '../api';

type Tab = 'overview' | 'rating' | 'games';

export const PlayerProfilePage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('overview');

  const profile = useQuery({
    queryKey: ['player', id, 'profile'],
    queryFn: () => playerApi.publicProfile(id!),
    enabled: !!id,
  });

  const history = useQuery({
    queryKey: ['player', id, 'rating-history'],
    queryFn: () => playerApi.ratingHistory(id!),
    enabled: !!id && tab === 'rating',
  });

  if (profile.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={100} />
        <Skeleton height={320} />
      </div>
    );
  }

  if (profile.isError || !profile.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert
          title="Jugador no disponible"
          message="No se pudo cargar el perfil del jugador."
          onRetry={() => profile.refetch()}
        />
      </div>
    );
  }

  const { profile: p, recentGames, stats } = profile.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || '—';

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <button className="btn btn-ghost" style={{ alignSelf: 'flex-start' }} onClick={() => navigate(-1)}>
        ← Volver
      </button>

      {/* Header */}
      <Card padded>
        <div style={{ display: 'flex', gap: 18, alignItems: 'center', flexWrap: 'wrap' }}>
          <div
            style={{
              width: 72,
              height: 72,
              borderRadius: '50%',
              background: 'var(--accent-dim)',
              border: '2px solid oklch(68% 0.20 150 / 0.35)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 28,
              color: 'var(--accent)',
              fontWeight: 700,
            }}
          >
            {(p.firstName?.[0] ?? '?') + (p.lastName?.[0] ?? '')}
          </div>
          <div style={{ flex: 1, minWidth: 200 }}>
            <div style={{ fontSize: 24, fontWeight: 700 }}>{fullName}</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 6 }}>
              {p.fideTitle && <Badge variant="gold">{p.fideTitle}</Badge>}
              {p.ageCategory && <Badge>{p.ageCategory}</Badge>}
              {p.clubName && <Badge variant="info">{p.clubName}</Badge>}
              {p.countryName && <Badge>{p.countryFlag ?? ''} {p.countryName}</Badge>}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {p.eloFideStandard != null && <RatingBadge rating={p.eloFideStandard} label="FIDE" />}
            {p.eloNational != null && <RatingBadge rating={p.eloNational} label="NAC" />}
            {p.eloFideRapid != null && <RatingBadge rating={p.eloFideRapid} label="RAPID" />}
            {p.eloFideBlitz != null && <RatingBadge rating={p.eloFideBlitz} label="BLITZ" />}
          </div>
        </div>
      </Card>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--border)' }}>
        {(['overview', 'rating', 'games'] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            style={{
              padding: '10px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: tab === t ? '2px solid var(--accent)' : '2px solid transparent',
              color: tab === t ? 'var(--accent)' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
            }}
          >
            {t === 'overview' ? 'Resumen' : t === 'rating' ? 'Rating' : 'Partidas'}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          <Card header="Total de partidas"><div style={{ fontSize: 28, fontWeight: 700 }}>{stats?.totalGames ?? 0}</div></Card>
          <Card header="Victorias"><div style={{ fontSize: 28, fontWeight: 700, color: 'var(--accent)' }}>{stats?.wins ?? 0}</div></Card>
          <Card header="Tablas"><div style={{ fontSize: 28, fontWeight: 700 }}>{stats?.draws ?? 0}</div></Card>
          <Card header="Derrotas"><div style={{ fontSize: 28, fontWeight: 700, color: 'var(--red)' }}>{stats?.losses ?? 0}</div></Card>
          <Card header="% Victoria"><div style={{ fontSize: 28, fontWeight: 700 }}>{stats?.winRate != null ? `${(stats.winRate * 100).toFixed(1)}%` : '—'}</div></Card>
          {stats?.favoriteOpening && (
            <Card header="Apertura favorita"><div style={{ fontSize: 15 }}>{stats.favoriteOpening}</div></Card>
          )}
        </div>
      )}

      {tab === 'rating' && (
        <Card header="Evolución de rating">
          {history.isLoading ? (
            <Skeleton height={280} />
          ) : history.isError ? (
            <ErrorAlert message="No se pudo cargar el histórico" onRetry={() => history.refetch()} />
          ) : !history.data || history.data.length === 0 ? (
            <EmptyState title="Sin historial" description="No hay datos de rating todavía" />
          ) : (
            <div style={{ width: '100%', height: 320 }}>
              <ResponsiveContainer>
                <LineChart data={(history.data as RatingHistoryPoint[]).map((h) => ({
                  date: h.recordedAt?.slice(0, 10),
                  rating: h.value,
                }))}>
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
      )}

      {tab === 'games' && (
        <Card header="Últimas partidas">
          {!recentGames || recentGames.length === 0 ? (
            <EmptyState title="Sin partidas" />
          ) : (
            <Table<Game>
              rows={recentGames}
              rowKey={(r) => r.id}
              columns={[
                { key: 'date', header: 'Fecha', render: (r) => r.playedAt?.slice(0, 10) ?? '—' },
                { key: 'white', header: 'Blancas', render: (r) => r.whiteName ?? `#${r.whitePlayerId}` },
                { key: 'black', header: 'Negras', render: (r) => r.blackName ?? `#${r.blackPlayerId}` },
                { key: 'result', header: 'Resultado', align: 'center', render: (r) => <Badge>{r.result}</Badge> },
                { key: 'opening', header: 'Apertura', render: (r) => r.openingName ?? '—' },
                { key: 'type', header: 'Tipo', render: (r) => <Badge variant={r.gameType === 'TOURNAMENT' ? 'info' : 'neutral'}>{r.gameType}</Badge> },
              ] as TableColumn<Game>[]}
            />
          )}
        </Card>
      )}
    </div>
  );
};
