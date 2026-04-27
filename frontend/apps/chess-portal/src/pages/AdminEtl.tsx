import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Skeleton, Table, TableColumn } from '@chessquery/ui-lib';
import { adminApi } from '../api';
import { circuitStateVariant, etlSources, formatDateTime } from '../portal-utils';

interface EtlLogRow {
  id: number;
  source: string;
  status: string;
  recordsProcessed: number;
  recordsFailed: number;
  startedAt: string | null;
  finishedAt: string | null;
  circuitBreakerState: string;
  errorMessage: string | null;
}

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

export const AdminEtlPage = () => {
  const queryClient = useQueryClient();

  const status = useQuery({
    queryKey: ['admin', 'etl', 'status'],
    queryFn: () => adminApi.etlStatus(),
  });

  const logs = useQuery({
    queryKey: ['admin', 'etl', 'logs'],
    queryFn: () => adminApi.etlLogs(50),
    refetchInterval: 10_000,
  });

  const sync = useMutation({
    mutationFn: (source: string) => adminApi.sync(source),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['admin', 'etl', 'status'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'etl', 'logs'] }),
        queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] }),
      ]);
    },
  });

  const sourceCards = useMemo(() => {
    const list = Array.isArray(status.data) ? status.data : [];
    const bySource = new Map(
      list.map((item) => {
        const r = asRecord(item);
        return [String(r.source ?? '').toUpperCase(), r] as const;
      }),
    );

    return etlSources.map((source) => {
      const entry = bySource.get(source.key) ?? {};
      const lastSync = asRecord(entry.lastSync);
      return {
        ...source,
        state: String(entry.circuitBreakerState ?? 'UNKNOWN'),
        failureCount: asNumber(lastSync.recordsFailed),
        failureThreshold: 5,
        lastSyncAt: typeof lastSync.finishedAt === 'string' ? lastSync.finishedAt : null,
      };
    });
  }, [status.data]);

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">ETL / Fuentes</div>
          <h1 className="page-title">Sincronización, circuit breakers y operación de fuentes externas.</h1>
          <p className="page-copy">
            Reproduje la lógica visual de tus capturas: alertas arriba, estados `CLOSED / OPEN / HALF_OPEN`, cards por
            fuente y foco operativo en la sincronización manual.
          </p>
        </div>
        <Badge variant={status.isError ? 'danger' : 'success'} dot pulse={!status.isError}>
          {status.isError ? 'ETL degradado' : 'ETL operativo'}
        </Badge>
      </section>

      {status.isLoading ? (
        <div style={{ display: 'grid', gap: 14 }}>
          <Skeleton height={110} />
          <div className="admin-metrics-grid">
            {Array.from({ length: 4 }).map((_, index) => (
              <Skeleton key={index} height={260} />
            ))}
          </div>
        </div>
      ) : status.isError ? (
        <ErrorAlert title="No se pudo cargar el estado ETL" onRetry={() => status.refetch()} />
      ) : (
        <>
          <Card className="etl-alert">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 24 }}>⚠</span>
              <div style={{ fontSize: 18, fontWeight: 700 }}>
                {sourceCards.filter((source) => source.state === 'OPEN').length} fuente(s) con errores.
              </div>
              <div style={{ color: 'var(--text-muted)' }}>
                El panel protege la sincronización usando circuit breakers por fuente.
              </div>
            </div>
          </Card>

          <div className="source-state-strip">
            {['CLOSED', 'OPEN', 'HALF_OPEN'].map((stateKey) => (
              <div key={stateKey} className="source-state-pill">
                <Badge variant={circuitStateVariant(stateKey)} dot>
                  {stateKey}
                </Badge>
                <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                  {stateKey === 'CLOSED'
                    ? 'flujo habilitado sin errores recientes'
                    : stateKey === 'OPEN'
                      ? 'fallos detectados, requests bloqueados'
                      : 'reconexión de prueba permitida'}
                </span>
              </div>
            ))}
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
              gap: 24,
            }}
          >
            {sourceCards.map((source) => (
              <Card key={source.key} className="source-card">
                <div style={{ display: 'grid', gap: 14 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'start' }}>
                    <div>
                      <div style={{ fontSize: 24, fontWeight: 700 }}>{source.icon} {source.label}</div>
                      <div style={{ color: 'var(--text-muted)' }}>{source.description}</div>
                    </div>
                    <Badge variant={circuitStateVariant(source.state)}>{source.state}</Badge>
                  </div>

                  <div className="etl-status-box" data-state={source.state}>
                    <div className="eyebrow">Estado circuit breaker</div>
                    <div style={{ fontSize: 18, lineHeight: 1.5 }}>
                      {source.state === 'CLOSED'
                        ? 'Circuito normal y sincronización habilitada.'
                        : source.state === 'OPEN'
                          ? 'Circuito abierto, nuevas requests bloqueadas.'
                          : 'Circuito en modo de prueba, esperando recuperación.'}
                    </div>
                    <div style={{ color: 'var(--text-muted)', marginTop: 10 }}>
                      Registros fallidos último sync: {source.failureCount}
                    </div>
                  </div>

                  <div className="metric-grid">
                    <div className="metric-card">
                      <div className="metric-label">Última sincronización</div>
                      <div className="metric-value" style={{ fontSize: 16 }}>
                        {source.lastSyncAt ? formatDateTime(source.lastSyncAt) : '—'}
                      </div>
                    </div>
                    <div className="metric-card">
                      <div className="metric-label">Circuit breaker</div>
                      <div className="metric-value" style={{ fontSize: 18 }}>
                        {source.state}
                      </div>
                    </div>
                  </div>

                  <Button
                    size="lg"
                    onClick={() => sync.mutate(source.key)}
                    loading={sync.isPending && sync.variables === source.key}
                  >
                    Sincronizar ahora
                  </Button>
                </div>
              </Card>
            ))}
          </div>

          {sync.isError ? (
            <ErrorAlert title="No se pudo lanzar la sincronización" message="Revisa la disponibilidad del BFF-Admin o del MS-ETL." />
          ) : null}

          <Card header="Actividad operacional · Logs ETL">
            {logs.isLoading ? (
              <Skeleton height={220} />
            ) : logs.isError ? (
              <ErrorAlert title="No se pudieron cargar los logs" onRetry={() => logs.refetch()} />
            ) : !logs.data || logs.data.length === 0 ? (
              <EmptyState title="Sin actividad registrada" description="Aún no hay sincronizaciones para mostrar." icon="♖" />
            ) : (
              <Table<EtlLogRow>
                rows={logs.data as EtlLogRow[]}
                rowKey={(row) => row.id}
                columns={[
                  { key: 'id', header: '#', render: (row) => `#${row.id}` },
                  { key: 'source', header: 'Fuente', render: (row) => <strong>{row.source}</strong> },
                  {
                    key: 'status',
                    header: 'Estado',
                    render: (row) => (
                      <Badge
                        variant={row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'}
                        dot
                      >
                        {row.status}
                      </Badge>
                    ),
                  },
                  { key: 'rp', header: 'OK', align: 'right', render: (row) => row.recordsProcessed },
                  { key: 'rf', header: 'Fallos', align: 'right', render: (row) => row.recordsFailed },
                  {
                    key: 'cb',
                    header: 'Breaker',
                    render: (row) => (
                      <Badge variant={circuitStateVariant(row.circuitBreakerState)} dot>
                        {row.circuitBreakerState}
                      </Badge>
                    ),
                  },
                  {
                    key: 'started',
                    header: 'Inicio',
                    render: (row) => (row.startedAt ? formatDateTime(row.startedAt) : '—'),
                  },
                  {
                    key: 'duration',
                    header: 'Duración',
                    align: 'right',
                    render: (row) => {
                      if (!row.startedAt || !row.finishedAt) return '—';
                      const ms = new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime();
                      return `${ms} ms`;
                    },
                  },
                  {
                    key: 'err',
                    header: 'Error',
                    render: (row) =>
                      row.errorMessage ? (
                        <span style={{ color: 'var(--red)', fontSize: 12 }}>{row.errorMessage}</span>
                      ) : (
                        '—'
                      ),
                  },
                ] as TableColumn<EtlLogRow>[]}
              />
            )}
          </Card>
        </>
      )}
    </div>
  );
};
