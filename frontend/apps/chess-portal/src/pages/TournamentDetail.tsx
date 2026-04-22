import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Badge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  StandingsTable,
  Table,
  TableColumn,
} from '@chessquery/ui-lib';
import { Tournament, Standing } from '@chessquery/shared';
import { tournamentApi } from '../api';

type Tab = 'info' | 'standings' | 'rounds';

const statusVariant = (s: Tournament['status']) =>
  s === 'IN_PROGRESS' ? 'success' : s === 'OPEN' ? 'info' : s === 'FINISHED' ? 'neutral' : 'warning';

interface StandingsResp {
  standings?: Standing[];
  entries?: Standing[];
}

export const TournamentDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('info');

  const detail = useQuery({
    queryKey: ['tournament', id],
    queryFn: () => tournamentApi.detail(id!),
    enabled: !!id,
  });

  const standings = useQuery({
    queryKey: ['tournament', id, 'standings'],
    queryFn: () => tournamentApi.standings(id!),
    enabled: !!id && tab === 'standings',
  });

  if (detail.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={120} />
        <Skeleton height={320} />
      </div>
    );
  }

  if (detail.isError || !detail.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="Torneo no encontrado" onRetry={() => detail.refetch()} />
      </div>
    );
  }

  const t = detail.data;
  const standingsData = standings.data as StandingsResp | Standing[] | undefined;
  const standingsList: Standing[] = Array.isArray(standingsData)
    ? standingsData
    : standingsData?.standings ?? standingsData?.entries ?? [];

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <button className="btn btn-ghost" style={{ alignSelf: 'flex-start' }} onClick={() => navigate('/tournaments')}>
        ← Volver a torneos
      </button>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, flexWrap: 'wrap' }}>
          <div>
            <h1 style={{ fontSize: 24, fontWeight: 700 }}>{t.name}</h1>
            <div style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
              <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
              <Badge variant="info">{t.format}</Badge>
              <Badge>{t.rounds} rondas</Badge>
            </div>
          </div>
          <div style={{ textAlign: 'right', fontSize: 13, color: 'var(--text-muted)' }}>
            <div>📅 {t.startDate} {t.endDate ? `→ ${t.endDate}` : ''}</div>
            {t.location && <div>📍 {t.location}</div>}
            <div>👥 {t.registered ?? 0} / {t.maxPlayers}</div>
          </div>
        </div>
      </Card>

      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--border)' }}>
        {(['info', 'standings', 'rounds'] as Tab[]).map((x) => (
          <button
            key={x}
            type="button"
            onClick={() => setTab(x)}
            style={{
              padding: '10px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: tab === x ? '2px solid var(--accent)' : '2px solid transparent',
              color: tab === x ? 'var(--accent)' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
            }}
          >
            {x === 'info' ? 'Información' : x === 'standings' ? 'Clasificación' : 'Rondas'}
          </button>
        ))}
      </div>

      {tab === 'info' && (
        <Card header="Información del torneo">
          <Table
            rows={[
              { k: 'Formato', v: t.format },
              { k: 'Estado', v: t.status },
              { k: 'Rondas', v: String(t.rounds) },
              { k: 'Inicio', v: t.startDate },
              { k: 'Fin', v: t.endDate ?? '—' },
              { k: 'Ubicación', v: t.location ?? '—' },
              { k: 'Cupos', v: `${t.registered ?? 0} / ${t.maxPlayers}` },
              { k: 'ELO mínimo', v: t.minElo != null ? String(t.minElo) : '—' },
              { k: 'ELO máximo', v: t.maxElo != null ? String(t.maxElo) : '—' },
              { k: 'Control de tiempo', v: t.timeControl ?? '—' },
            ]}
            rowKey={(r) => r.k}
            columns={[
              { key: 'k', header: 'Campo', width: 200, render: (r) => <strong>{r.k}</strong> },
              { key: 'v', header: 'Valor', render: (r) => r.v },
            ] as TableColumn<{ k: string; v: string }>[]}
          />
        </Card>
      )}

      {tab === 'standings' && (
        <Card header="Clasificación">
          {standings.isLoading ? (
            <Skeleton height={260} />
          ) : standings.isError ? (
            <ErrorAlert message="No se pudo cargar la clasificación" onRetry={() => standings.refetch()} />
          ) : standingsList.length === 0 ? (
            <EmptyState title="Aún sin clasificación" description="El torneo aún no genera standings" />
          ) : (
            <StandingsTable entries={standingsList} />
          )}
        </Card>
      )}

      {tab === 'rounds' && (
        <Card header="Rondas">
          <EmptyState
            title="Pendiente"
            description="La vista de pareos por ronda estará disponible próximamente"
            icon="♞"
          />
        </Card>
      )}
    </div>
  );
};
