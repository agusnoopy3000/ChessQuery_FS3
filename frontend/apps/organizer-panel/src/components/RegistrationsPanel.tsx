import { useMemo, useState } from 'react';
import { Badge, Button, Card, EmptyState, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import type { RegistrationRow } from '../api';

interface Props {
  registrations: RegistrationRow[];
  loading?: boolean;
  error?: string | null;
  onApprove: (registrationId: number) => void;
  onReject: (registrationId: number, reason: string | undefined) => void;
  busyId?: number | null;
}

const STATUS_BADGE: Record<RegistrationRow['status'], { label: string; variant: Parameters<typeof Badge>[0]['variant'] }> = {
  PENDING:   { label: 'Pendiente',  variant: 'warning' },
  CONFIRMED: { label: 'Confirmada', variant: 'success' },
  REJECTED:  { label: 'Rechazada',  variant: 'neutral' },
  CANCELLED: { label: 'Cancelada',  variant: 'neutral' },
};

const STATUS_ORDER: RegistrationRow['status'][] = ['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'];

export const RegistrationsPanel = ({ registrations, loading, error, onApprove, onReject, busyId }: Props) => {
  const [filter, setFilter] = useState<'ALL' | RegistrationRow['status']>('ALL');
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: registrations.length, PENDING: 0, CONFIRMED: 0, REJECTED: 0, CANCELLED: 0 };
    for (const r of registrations) c[r.status] = (c[r.status] ?? 0) + 1;
    return c;
  }, [registrations]);

  const sorted = useMemo(() => {
    const filtered = filter === 'ALL' ? registrations : registrations.filter((r) => r.status === filter);
    return [...filtered].sort((a, b) => {
      const sa = STATUS_ORDER.indexOf(a.status);
      const sb = STATUS_ORDER.indexOf(b.status);
      if (sa !== sb) return sa - sb;
      return (a.registeredAt ?? '').localeCompare(b.registeredAt ?? '');
    });
  }, [registrations, filter]);

  const tabs: Array<['ALL' | RegistrationRow['status'], string]> = [
    ['ALL', `Todas (${counts.ALL})`],
    ['PENDING', `Pendientes (${counts.PENDING ?? 0})`],
    ['CONFIRMED', `Confirmadas (${counts.CONFIRMED ?? 0})`],
    ['REJECTED', `Rechazadas (${counts.REJECTED ?? 0})`],
  ];

  return (
    <Card
      header={
        <div className="card-header-row">
          <span>Inscripciones</span>
          {counts.PENDING > 0 && (
            <Badge variant="warning">{counts.PENDING} pendiente{counts.PENDING === 1 ? '' : 's'}</Badge>
          )}
        </div>
      }
    >
      <div style={{ display: 'flex', gap: 6, marginBottom: 14, flexWrap: 'wrap' }}>
        {tabs.map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setFilter(key)}
            className="tab-btn"
            data-active={filter === key}
          >
            {label}
          </button>
        ))}
      </div>

      {loading ? (
        <Skeleton height={120} />
      ) : error ? (
        <ErrorAlert title="No se pudieron cargar las inscripciones" message={error} />
      ) : sorted.length === 0 ? (
        <EmptyState
          title={filter === 'ALL' ? 'Sin inscripciones' : 'Sin resultados en este filtro'}
          description={filter === 'ALL' ? 'Cuando los jugadores se inscriban, aparecerán aquí.' : 'Cambia de filtro para ver otras inscripciones.'}
          icon="♟"
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {sorted.map((r) => {
            const isBusy = busyId === r.id;
            const isRejecting = rejectingId === r.id;
            const elo = r.playerEloFide ?? r.playerEloNational ?? r.seedRating;
            return (
              <div
                key={r.id}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr auto',
                  gap: 12,
                  padding: 12,
                  background: 'var(--surface-2, #15171a)',
                  border: '1px solid var(--border, #2a2d27)',
                  borderRadius: 8,
                  alignItems: 'center',
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <strong>{r.playerName ?? `Jugador #${r.playerId}`}</strong>
                    <Badge variant={STATUS_BADGE[r.status].variant}>{STATUS_BADGE[r.status].label}</Badge>
                  </div>
                  <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                    {elo ? `ELO ${elo}` : 'ELO no disponible'} · inscrito el{' '}
                    {r.registeredAt ? new Date(r.registeredAt).toLocaleDateString() : '—'}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  {r.status === 'PENDING' && !isRejecting && (
                    <>
                      <Button size="sm" variant="primary" onClick={() => onApprove(r.id)} loading={isBusy}>
                        Aprobar
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => setRejectingId(r.id)} disabled={isBusy}>
                        Rechazar
                      </Button>
                    </>
                  )}
                  {isRejecting && (
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <input
                        autoFocus
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="Razón (opcional)"
                        style={{
                          background: '#0e100d', border: '1px solid #2a2d27', borderRadius: 6,
                          padding: '6px 10px', color: '#e8ead4', fontSize: 12, width: 180,
                        }}
                      />
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          onReject(r.id, rejectReason.trim() || undefined);
                          setRejectingId(null);
                          setRejectReason('');
                        }}
                        loading={isBusy}
                      >
                        Confirmar
                      </Button>
                      <Button size="sm" variant="ghost" onClick={() => { setRejectingId(null); setRejectReason(''); }}>
                        Cancelar
                      </Button>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      <style>{`
        .tab-btn {
          background: transparent;
          border: 1px solid var(--border, #2a2d27);
          color: var(--text-muted, #7a7d6e);
          padding: 6px 12px;
          border-radius: 6px;
          font-size: 12px;
          font-family: inherit;
          cursor: pointer;
          transition: all 0.15s;
        }
        .tab-btn:hover { color: #e8ead4; border-color: #4a4d40; }
        .tab-btn[data-active='true'] {
          background: rgba(106,191,116,0.1);
          border-color: #4a7c59;
          color: #6abf74;
        }
      `}</style>
    </Card>
  );
};
