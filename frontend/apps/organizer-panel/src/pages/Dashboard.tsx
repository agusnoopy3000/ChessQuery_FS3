import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Button,
  Badge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Table,
  TableColumn,
} from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { organizerApi } from '../api';

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const OrganizerDashboardPage = () => {
  const navigate = useNavigate();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['organizer', 'tournaments'],
    queryFn: () => organizerApi.listTournaments(),
  });

  const rows = unwrap<Tournament>(data);
  const counters = {
    total: rows.length,
    inProgress: rows.filter((t) => t.status === 'IN_PROGRESS').length,
    open: rows.filter((t) => t.status === 'OPEN').length,
    finished: rows.filter((t) => t.status === 'FINISHED').length,
  };

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700 }}>Mis torneos</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Gestiona tus competencias</p>
        </div>
        <Button onClick={() => navigate('/tournaments/new')}>+ Nuevo torneo</Button>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        <Card header="Total"><div style={{ fontSize: 28, fontWeight: 700 }}>{counters.total}</div></Card>
        <Card header="En curso"><div style={{ fontSize: 28, fontWeight: 700, color: 'var(--accent)' }}>{counters.inProgress}</div></Card>
        <Card header="Abiertos"><div style={{ fontSize: 28, fontWeight: 700, color: 'var(--blue)' }}>{counters.open}</div></Card>
        <Card header="Finalizados"><div style={{ fontSize: 28, fontWeight: 700, color: 'var(--text-muted)' }}>{counters.finished}</div></Card>
      </div>

      <Card header="Listado">
        {isLoading ? (
          <div style={{ display: 'grid', gap: 8 }}>
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} height={40} />)}
          </div>
        ) : isError ? (
          <ErrorAlert message="No se pudo cargar el listado" onRetry={() => refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState
            title="Aún no hay torneos"
            description="Comienza creando tu primer torneo"
            action={<Button onClick={() => navigate('/tournaments/new')}>Crear torneo</Button>}
          />
        ) : (
          <Table<Tournament>
            rows={rows}
            rowKey={(r) => r.id}
            columns={[
              {
                key: 'name',
                header: 'Nombre',
                render: (r) => (
                  <button
                    className="btn btn-ghost"
                    onClick={() => navigate(`/tournaments/${r.id}`)}
                    style={{ padding: '2px 6px', fontWeight: 600 }}
                  >
                    {r.name}
                  </button>
                ),
              },
              { key: 'format', header: 'Formato', render: (r) => <Badge variant="info">{r.format}</Badge> },
              { key: 'status', header: 'Estado', render: (r) => <Badge>{r.status}</Badge> },
              { key: 'start', header: 'Inicio', render: (r) => r.startDate },
              { key: 'players', header: 'Inscritos', align: 'right', render: (r) => `${r.registered ?? 0} / ${r.maxPlayers}` },
              {
                key: 'actions',
                header: '',
                align: 'right',
                render: (r) => (
                  <Button size="sm" variant="ghost" onClick={() => navigate(`/tournaments/${r.id}`)}>
                    Gestionar →
                  </Button>
                ),
              },
            ] as TableColumn<Tournament>[]}
          />
        )}
      </Card>
    </div>
  );
};
