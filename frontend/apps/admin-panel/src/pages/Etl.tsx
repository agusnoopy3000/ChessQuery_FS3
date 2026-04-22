import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
import { EtlLogEntry, CircuitBreakerState } from '@chessquery/shared';
import { adminApi } from '../api';

const cbVariant = (s: CircuitBreakerState) =>
  s === 'CLOSED' ? 'success' : s === 'HALF_OPEN' ? 'warning' : 'danger';

const statusVariant = (s: EtlLogEntry['status']) =>
  s === 'SUCCESS' ? 'success' : s === 'RUNNING' ? 'info' : 'danger';

export const EtlPage = () => {
  const qc = useQueryClient();

  const status = useQuery({
    queryKey: ['admin', 'etl', 'status'],
    queryFn: () => adminApi.etlStatus(),
    refetchInterval: 15_000,
  });

  const logs = useQuery({
    queryKey: ['admin', 'etl', 'logs'],
    queryFn: () => adminApi.etlLogs(),
    refetchInterval: 15_000,
  });

  const trigger = useMutation({
    mutationFn: (source: string) => adminApi.triggerEtl(source),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'etl', 'status'] });
      qc.invalidateQueries({ queryKey: ['admin', 'etl', 'logs'] });
    },
  });

  const cbs = status.data?.circuitBreakers ?? {};

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div>
        <h1 style={{ fontSize: 24, fontWeight: 700 }}>ETL</h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 13 }}>Sincronización FIDE y Lichess</p>
      </div>

      <Card header="Circuit Breakers">
        {status.isLoading ? (
          <Skeleton height={40} />
        ) : status.isError ? (
          <ErrorAlert message="No se pudo cargar el estado ETL" onRetry={() => status.refetch()} />
        ) : Object.keys(cbs).length === 0 ? (
          <EmptyState title="Sin circuit breakers" />
        ) : (
          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
            {Object.entries(cbs).map(([name, s]) => (
              <div key={name} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 10px', border: '1px solid var(--border)', borderRadius: 8 }}>
                <strong style={{ fontSize: 12 }}>{name}</strong>
                <Badge variant={cbVariant(s.state)}>{s.state}</Badge>
                <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  {s.failureCount}/{s.failureThreshold} fallos
                </span>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card header="Disparar sincronización">
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Button onClick={() => trigger.mutate('fide')} loading={trigger.isPending && trigger.variables === 'fide'}>
            ⟳ Sincronizar FIDE
          </Button>
          <Button
            variant="secondary"
            onClick={() => trigger.mutate('lichess')}
            loading={trigger.isPending && trigger.variables === 'lichess'}
          >
            ⟳ Sincronizar Lichess
          </Button>
        </div>
        {trigger.isError && (
          <div style={{ marginTop: 10, color: 'var(--red)', fontSize: 12 }}>
            ⚠ No se pudo iniciar la sincronización
          </div>
        )}
      </Card>

      <Card header="Historial">
        {logs.isLoading ? (
          <Skeleton height={200} />
        ) : !logs.data || logs.data.length === 0 ? (
          <EmptyState title="Sin ejecuciones previas" />
        ) : (
          <Table<EtlLogEntry>
            rows={logs.data}
            rowKey={(r) => r.id}
            columns={[
              { key: 'source', header: 'Fuente', render: (r) => <Badge variant="info">{r.source}</Badge> },
              { key: 'status', header: 'Estado', render: (r) => <Badge variant={statusVariant(r.status)}>{r.status}</Badge> },
              { key: 'start', header: 'Inicio', render: (r) => r.startedAt?.slice(0, 16).replace('T', ' ') },
              { key: 'end', header: 'Fin', render: (r) => r.finishedAt?.slice(0, 16).replace('T', ' ') ?? '—' },
              { key: 'ok', header: 'OK', align: 'right', render: (r) => r.recordsProcessed },
              { key: 'fail', header: 'Fallos', align: 'right', render: (r) => r.recordsFailed },
              { key: 'cb', header: 'CB', render: (r) => (r.cbState ? <Badge variant={cbVariant(r.cbState)}>{r.cbState}</Badge> : '—') },
              {
                key: 'err',
                header: 'Error',
                render: (r) =>
                  r.errorMessage ? (
                    <span style={{ color: 'var(--red)', fontSize: 11 }}>{r.errorMessage}</span>
                  ) : (
                    '—'
                  ),
              },
            ] as TableColumn<EtlLogEntry>[]}
          />
        )}
      </Card>
    </div>
  );
};
