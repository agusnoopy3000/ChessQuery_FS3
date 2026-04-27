import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  Select,
  Badge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Input,
} from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { tournamentApi } from '../api';

const STATUS_OPTIONS = [
  { value: '', label: 'Todos los estados' },
  { value: 'DRAFT', label: 'Borrador' },
  { value: 'OPEN', label: 'Inscripciones abiertas' },
  { value: 'IN_PROGRESS', label: 'En curso' },
  { value: 'FINISHED', label: 'Finalizado' },
];

const FORMAT_OPTIONS = [
  { value: '', label: 'Todos los formatos' },
  { value: 'SWISS', label: 'Suizo' },
  { value: 'ROUND_ROBIN', label: 'Todos contra todos' },
  { value: 'KNOCKOUT', label: 'Eliminación' },
];

const statusVariant = (s: Tournament['status']) =>
  s === 'IN_PROGRESS' ? 'success' : s === 'OPEN' ? 'info' : s === 'FINISHED' ? 'neutral' : 'warning';

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const TournamentsPage = () => {
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [format, setFormat] = useState('');
  const [q, setQ] = useState('');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['tournaments'],
    queryFn: () => tournamentApi.list(),
  });

  const all = unwrap<Tournament>(data);
  const filtered = useMemo(
    () =>
      all.filter(
        (t) =>
          (!status || t.status === status) &&
          (!format || t.format === format) &&
          (!q || t.name.toLowerCase().includes(q.toLowerCase())),
      ),
    [all, status, format, q],
  );

  return (
    <div style={{ padding: 28 }}>
      <h1 style={{ fontSize: 22, marginBottom: 4 }}>Torneos</h1>
      <p style={{ color: 'var(--text-muted)', fontSize: 13, marginBottom: 20 }}>
        Explora competencias actuales y pasadas
      </p>

      <div style={{ display: 'flex', gap: 10, marginBottom: 20, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 220 }}>
          <Input
            placeholder="Buscar torneo…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            leftIcon={<span>🔍</span>}
          />
        </div>
        <Select value={status} onChange={(e) => setStatus(e.target.value)} options={STATUS_OPTIONS} style={{ width: 220 }} />
        <Select value={format} onChange={(e) => setFormat(e.target.value)} options={FORMAT_OPTIONS} style={{ width: 220 }} />
      </div>

      {isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 14 }}>
          {Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} height={160} />)}
        </div>
      ) : isError ? (
        <ErrorAlert message="No se pudieron cargar los torneos" onRetry={() => refetch()} />
      ) : filtered.length === 0 ? (
        <Card>
          <EmptyState title="Sin torneos" description="Prueba cambiando los filtros" icon="♞" />
        </Card>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 14 }}>
          {filtered.map((t) => (
            <Card
              key={t.id}
              hover
              style={{ cursor: 'pointer' }}
              onClick={() => navigate(`/tournaments/${t.id}`)}
              header={
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t.name}</span>
                  <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
                </div>
              }
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
                <div style={{ display: 'flex', gap: 6, color: 'var(--text-muted)' }}>
                  <span>♞</span>
                  <span>{t.format}</span>
                  <span>·</span>
                  <span>{t.rounds} rondas</span>
                </div>
                <div style={{ color: 'var(--text-muted)' }}>
                  📅 {t.startDate} {t.endDate ? `→ ${t.endDate}` : ''}
                </div>
                {t.location && (
                  <div style={{ color: 'var(--text-muted)' }}>📍 {t.location}</div>
                )}
                <div style={{ color: 'var(--text-muted)' }}>
                  👥 {t.registered ?? 0} / {t.maxPlayers}
                </div>
                {(t.minElo || t.maxElo) && (
                  <div style={{ color: 'var(--text-muted)' }}>
                    ELO: {t.minElo ?? '—'} – {t.maxElo ?? '—'}
                  </div>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};
