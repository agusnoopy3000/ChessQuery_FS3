import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Select,
  Table,
  TableColumn,
  Badge,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
} from '@chessquery/ui-lib';
import { Player } from '@chessquery/shared';
import { playerApi } from '../api';

const CATEGORIES = [
  { value: '', label: 'Todas las categorías' },
  { value: 'OPEN', label: 'Open' },
  { value: 'SUB8', label: 'Sub 8' },
  { value: 'SUB10', label: 'Sub 10' },
  { value: 'SUB12', label: 'Sub 12' },
  { value: 'SUB14', label: 'Sub 14' },
  { value: 'SUB16', label: 'Sub 16' },
  { value: 'SUB18', label: 'Sub 18' },
  { value: 'SUB20', label: 'Sub 20' },
  { value: 'SENIOR', label: 'Senior' },
];

const REGIONS = [
  { value: '', label: 'Todo Chile' },
  { value: 'RM', label: 'Región Metropolitana' },
  { value: 'V', label: 'Valparaíso' },
  { value: 'VIII', label: 'Biobío' },
  { value: 'IX', label: 'Araucanía' },
  { value: 'X', label: 'Los Lagos' },
];

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const RankingsPage = () => {
  const navigate = useNavigate();
  const [category, setCategory] = useState('');
  const [region, setRegion] = useState('');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['rankings', category, region],
    queryFn: () => playerApi.rankings(category || undefined, region || undefined),
  });

  const rows = unwrap<Player>(data);

  return (
    <div style={{ padding: 28 }}>
      <h1 style={{ fontSize: 22, marginBottom: 4 }}>Ranking nacional</h1>
      <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 20 }}>
        Jugadores de Chile ordenados por ELO
      </p>

      <Card
        header={
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
            <span>Filtros</span>
            <div style={{ display: 'flex', gap: 10, marginLeft: 'auto' }}>
              <Select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                options={CATEGORIES}
                style={{ width: 200 }}
              />
              <Select
                value={region}
                onChange={(e) => setRegion(e.target.value)}
                options={REGIONS}
                style={{ width: 220 }}
              />
            </div>
          </div>
        }
      >
        {isLoading ? (
          <div style={{ display: 'grid', gap: 8 }}>
            {Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} height={36} />)}
          </div>
        ) : isError ? (
          <ErrorAlert message="No se pudo cargar el ranking" onRetry={() => refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title="Sin jugadores" description="Prueba con otros filtros" />
        ) : (
          <Table<Player>
            rows={rows}
            rowKey={(r) => r.id}
            columns={[
              {
                key: 'rank',
                header: '#',
                width: 50,
                render: (_r, i) => (
                  <span
                    style={{
                      fontWeight: 700,
                      color: i < 3 ? 'var(--accent)' : 'var(--text-muted)',
                      fontFamily: 'monospace',
                    }}
                  >
                    {i + 1}
                  </span>
                ),
              },
              {
                key: 'name',
                header: 'Jugador',
                render: (r) => (
                  <button
                    className="btn btn-ghost"
                    onClick={() => navigate(`/player/${r.id}`)}
                    style={{ padding: '2px 6px', fontSize: 13, fontWeight: 600 }}
                  >
                    {r.firstName} {r.lastName}
                  </button>
                ),
              },
              { key: 'title', header: 'Título', render: (r) => (r.fideTitle ? <Badge variant="gold">{r.fideTitle}</Badge> : '—') },
              { key: 'club', header: 'Club', render: (r) => r.clubName ?? '—' },
              { key: 'category', header: 'Cat.', render: (r) => r.ageCategory ?? '—' },
              {
                key: 'fide',
                header: 'FIDE',
                align: 'right',
                render: (r) => (r.eloFideStandard != null ? <RatingBadge rating={r.eloFideStandard} /> : '—'),
              },
              {
                key: 'nac',
                header: 'Nacional',
                align: 'right',
                render: (r) => (r.eloNational != null ? <RatingBadge rating={r.eloNational} /> : '—'),
              },
            ] as TableColumn<Player>[]}
          />
        )}
      </Card>
    </div>
  );
};
