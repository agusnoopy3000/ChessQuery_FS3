import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Input,
  Badge,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Table,
  TableColumn,
} from '@chessquery/ui-lib';
import { Player } from '@chessquery/shared';
import { adminApi } from '../api';

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const UsersPage = () => {
  const [q, setQ] = useState('');
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.users(),
  });

  const all = unwrap<Player>(data);
  const filtered = useMemo(() => {
    if (!q.trim()) return all;
    const needle = q.toLowerCase();
    return all.filter(
      (p) =>
        `${p.firstName} ${p.lastName}`.toLowerCase().includes(needle) ||
        p.fideId?.toLowerCase().includes(needle) ||
        p.lichessUsername?.toLowerCase().includes(needle),
    );
  }, [all, q]);

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <h1 style={{ fontSize: 24, fontWeight: 700 }}>Usuarios</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>{all.length} jugadores registrados</p>
      </div>

      <div style={{ maxWidth: 480 }}>
        <Input
          placeholder="Filtrar por nombre, FIDE ID o Lichess…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          leftIcon={<span>🔍</span>}
        />
      </div>

      <Card>
        {isLoading ? (
          <div style={{ display: 'grid', gap: 8 }}>
            {Array.from({ length: 10 }).map((_, i) => <Skeleton key={i} height={36} />)}
          </div>
        ) : isError ? (
          <ErrorAlert message="No se pudo cargar usuarios" onRetry={() => refetch()} />
        ) : filtered.length === 0 ? (
          <EmptyState title="Sin usuarios" description={q ? 'Sin coincidencias' : undefined} />
        ) : (
          <Table<Player>
            rows={filtered}
            rowKey={(r) => r.id}
            columns={[
              { key: 'id', header: 'ID', width: 60, render: (r) => r.id },
              { key: 'name', header: 'Nombre', render: (r) => `${r.firstName} ${r.lastName}` },
              { key: 'rut', header: 'RUT', render: (r) => r.rut ?? '—' },
              { key: 'fide', header: 'FIDE ID', render: (r) => r.fideId ?? '—' },
              { key: 'lichess', header: 'Lichess', render: (r) => r.lichessUsername ?? '—' },
              { key: 'title', header: 'Título', render: (r) => (r.fideTitle ? <Badge variant="gold">{r.fideTitle}</Badge> : '—') },
              { key: 'club', header: 'Club', render: (r) => r.clubName ?? '—' },
              {
                key: 'rating',
                header: 'ELO',
                align: 'right',
                render: (r) =>
                  r.eloFideStandard != null ? (
                    <RatingBadge rating={r.eloFideStandard} />
                  ) : r.eloNational != null ? (
                    <RatingBadge rating={r.eloNational} />
                  ) : (
                    '—'
                  ),
              },
            ] as TableColumn<Player>[]}
          />
        )}
      </Card>
    </div>
  );
};
