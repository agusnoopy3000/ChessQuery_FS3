import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import type { Tournament } from '@chessquery/shared';
import { tournamentApi } from '../api';

const fontStack = "'Space Grotesk', system-ui, sans-serif";

const STATUS_TABS: Array<{ key: 'ALL' | Tournament['status']; label: string }> = [
  { key: 'ALL', label: 'Todos' },
  { key: 'OPEN', label: 'Inscripciones abiertas' },
  { key: 'IN_PROGRESS', label: 'En curso' },
  { key: 'FINISHED', label: 'Finalizados' },
];

const FORMAT_OPTIONS: Array<{ key: '' | Tournament['format']; label: string }> = [
  { key: '', label: 'Todos los formatos' },
  { key: 'SWISS', label: 'Suizo' },
  { key: 'ROUND_ROBIN', label: 'Todos contra todos' },
  { key: 'KNOCKOUT', label: 'Eliminación' },
];

const statusTone: Record<Tournament['status'], { bg: string; fg: string; border: string; accent: string }> = {
  DRAFT: {
    bg: 'rgba(255,255,255,0.03)',
    fg: '#7a7d6e',
    border: 'rgba(255,255,255,0.08)',
    accent: 'rgba(255,255,255,0.2)',
  },
  OPEN: {
    bg: 'linear-gradient(135deg, rgba(106,191,116,0.08) 0%, rgba(20,18,16,0.62) 60%)',
    fg: '#6abf74',
    border: 'rgba(106,191,116,0.3)',
    accent: '#6abf74',
  },
  IN_PROGRESS: {
    bg: 'linear-gradient(135deg, rgba(240,185,78,0.08) 0%, rgba(20,18,16,0.62) 60%)',
    fg: '#f0b94e',
    border: 'rgba(240,185,78,0.3)',
    accent: '#f0b94e',
  },
  FINISHED: {
    bg: 'rgba(20,18,16,0.62)',
    fg: '#7a7d6e',
    border: 'rgba(255,255,255,0.06)',
    accent: 'rgba(255,255,255,0.15)',
  },
};

const statusLabel: Record<Tournament['status'], string> = {
  DRAFT: 'Borrador',
  OPEN: '🔥 Abierto',
  IN_PROGRESS: 'En curso',
  FINISHED: 'Finalizado',
};

const formatLabel: Record<Tournament['format'], string> = {
  SWISS: 'Suizo',
  ROUND_ROBIN: 'Round-Robin',
  KNOCKOUT: 'Eliminación',
};

const formatDateNice = (iso: string): string => {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('es-CL', { day: '2-digit', month: 'short', year: 'numeric' });
};

const unwrap = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const TournamentsPage = () => {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState<'ALL' | Tournament['status']>('ALL');
  const [formatFilter, setFormatFilter] = useState<'' | Tournament['format']>('');
  const [search, setSearch] = useState('');

  const queryClient = useQueryClient();
  const { data, isLoading, isError, isFetching, refetch } = useQuery({
    queryKey: ['tournaments'],
    queryFn: () => tournamentApi.list(),
  });

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['tournaments'] });
  };

  const all = useMemo(() => {
    const list = unwrap<Tournament>(data);
    const seen = new Set<number>();
    const out: Tournament[] = [];
    for (const t of list) {
      if (seen.has(t.id)) continue;
      seen.add(t.id);
      out.push(t);
    }
    return out;
  }, [data]);

  const counts = useMemo(() => {
    const c: Record<string, number> = { ALL: all.length, OPEN: 0, IN_PROGRESS: 0, FINISHED: 0, DRAFT: 0 };
    for (const t of all) c[t.status] = (c[t.status] ?? 0) + 1;
    return c;
  }, [all]);

  const filtered = useMemo(
    () =>
      all.filter(
        (t) =>
          (statusFilter === 'ALL' || t.status === statusFilter) &&
          (!formatFilter || t.format === formatFilter) &&
          (!search || t.name.toLowerCase().includes(search.toLowerCase())),
      ),
    [all, statusFilter, formatFilter, search],
  );

  return (
    <div
      style={{
        padding: 28,
        maxWidth: 1100,
        margin: '0 auto',
        fontFamily: fontStack,
        animation: 'cq-fade-up 0.3s ease-out',
      }}
    >
      <style>{`
        @keyframes cq-fade-up {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes cq-spin { to { transform: rotate(360deg); } }
      `}</style>

      {/* Header */}
      <div style={{ marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', gap: 12, flexWrap: 'wrap' }}>
        <div>
          <div
            style={{
              fontSize: 11,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: 'var(--cq-text-muted, #4a4d40)',
              fontFamily: 'Space Mono, monospace',
              marginBottom: 6,
            }}
          >
            Catálogo de torneos
          </div>
          <h1 style={{ fontSize: 28, fontWeight: 700, letterSpacing: '-0.02em', margin: 0 }}>
            Torneos
          </h1>
          <p style={{ fontSize: 13, color: 'var(--cq-text-dim, #7a7d6e)', marginTop: 6, marginBottom: 0 }}>
            Explora competencias actuales, inscríbete y sigue tus standings.
          </p>
        </div>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={isFetching}
          title="Actualizar lista"
          aria-label="Actualizar lista"
          style={{
            background: 'rgba(255,255,255,0.04)',
            border: '1px solid var(--cq-border, #2a2d27)',
            borderRadius: 8,
            padding: '8px 14px',
            color: 'var(--cq-text-dim, #7a7d6e)',
            cursor: isFetching ? 'wait' : 'pointer',
            fontSize: 13,
            fontFamily: 'inherit',
            display: 'flex', alignItems: 'center', gap: 8,
            transition: 'border-color 0.15s, color 0.15s',
          }}
          onMouseEnter={(e) => {
            if (!isFetching) {
              e.currentTarget.style.borderColor = 'rgba(106,191,116,0.4)';
              e.currentTarget.style.color = '#6abf74';
            }
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.borderColor = 'var(--cq-border, #2a2d27)';
            e.currentTarget.style.color = 'var(--cq-text-dim, #7a7d6e)';
          }}
        >
          <span style={{ display: 'inline-block', animation: isFetching ? 'cq-spin 0.8s linear infinite' : 'none' }}>↻</span>
          {isFetching ? 'Actualizando…' : 'Actualizar'}
        </button>
      </div>

      {/* Tabs por estado */}
      <div
        style={{
          display: 'flex',
          gap: 6,
          marginBottom: 14,
          flexWrap: 'wrap',
          borderBottom: '1px solid var(--cq-border, #2a2d27)',
        }}
      >
        {STATUS_TABS.map((tab) => {
          const active = statusFilter === tab.key;
          const count = counts[tab.key] ?? 0;
          return (
            <button
              key={tab.key}
              onClick={() => setStatusFilter(tab.key)}
              style={{
                background: 'transparent',
                border: 'none',
                borderBottom: `2px solid ${active ? '#6abf74' : 'transparent'}`,
                color: active ? '#6abf74' : 'var(--cq-text-dim, #7a7d6e)',
                padding: '10px 14px',
                fontFamily: 'inherit',
                fontSize: 13,
                fontWeight: active ? 600 : 500,
                cursor: 'pointer',
                transition: 'color 0.15s, border-color 0.15s',
              }}
            >
              {tab.label}
              {count > 0 && (
                <span
                  style={{
                    marginLeft: 6,
                    fontSize: 11,
                    padding: '1px 7px',
                    borderRadius: 999,
                    background: active ? 'rgba(106,191,116,0.15)' : 'rgba(255,255,255,0.05)',
                    color: active ? '#6abf74' : 'var(--cq-text-muted, #4a4d40)',
                    fontFamily: 'Space Mono, monospace',
                  }}
                >
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Filtros secundarios */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 20, flexWrap: 'wrap' }}>
        <input
          placeholder="🔍 Buscar torneo…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{
            flex: 1,
            minWidth: 220,
            background: 'var(--cq-input-bg, #0e100d)',
            border: '1px solid var(--cq-border, #2a2d27)',
            borderRadius: 8,
            padding: '10px 14px',
            color: 'var(--cq-text, #e8ead4)',
            fontSize: 14,
            fontFamily: 'inherit',
            outline: 'none',
          }}
        />
        <select
          value={formatFilter}
          onChange={(e) => setFormatFilter(e.target.value as '' | Tournament['format'])}
          style={{
            background: 'var(--cq-input-bg, #0e100d)',
            border: '1px solid var(--cq-border, #2a2d27)',
            borderRadius: 8,
            padding: '10px 14px',
            color: 'var(--cq-text, #e8ead4)',
            fontSize: 14,
            fontFamily: 'inherit',
            outline: 'none',
            cursor: 'pointer',
          }}
        >
          {FORMAT_OPTIONS.map((opt) => (
            <option key={opt.key} value={opt.key}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* Cards */}
      {isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 14 }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} height={170} />
          ))}
        </div>
      ) : isError ? (
        <ErrorAlert message="No se pudieron cargar los torneos" onRetry={() => refetch()} />
      ) : filtered.length === 0 ? (
        <div
          style={{
            background: 'rgba(20,18,16,0.62)',
            border: '1px solid var(--cq-border, #2a2d27)',
            borderRadius: 18,
            padding: '48px 20px',
            textAlign: 'center',
            color: 'var(--cq-text-dim, #7a7d6e)',
          }}
        >
          <div style={{ fontSize: 36, marginBottom: 10 }}>♞</div>
          <div style={{ fontSize: 16, fontWeight: 600, color: 'var(--cq-text, #e8ead4)', marginBottom: 6 }}>
            Sin torneos en este filtro
          </div>
          <div style={{ fontSize: 13 }}>
            {statusFilter === 'OPEN'
              ? 'No hay torneos con inscripciones abiertas en este momento.'
              : 'Prueba cambiando los filtros.'}
          </div>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 14 }}>
          {filtered.map((t) => (
            <TournamentCard key={t.id} tournament={t} onClick={() => navigate(`/tournaments/${t.id}`)} />
          ))}
        </div>
      )}
    </div>
  );
};

const TournamentCard = ({ tournament, onClick }: { tournament: Tournament; onClick: () => void }) => {
  const tone = statusTone[tournament.status];
  const fillPct = tournament.maxPlayers
    ? Math.min(100, ((tournament.registered ?? 0) / tournament.maxPlayers) * 100)
    : 0;
  const isOpen = tournament.status === 'OPEN';

  return (
    <button
      onClick={onClick}
      style={{
        textAlign: 'left',
        background: tone.bg,
        border: `1px solid ${tone.border}`,
        borderRadius: 14,
        padding: 0,
        overflow: 'hidden',
        cursor: 'pointer',
        fontFamily: 'inherit',
        color: 'var(--cq-text, #e8ead4)',
        transition: 'transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.transform = 'translateY(-3px)';
        e.currentTarget.style.borderColor = tone.accent;
        e.currentTarget.style.boxShadow = `0 12px 32px rgba(0,0,0,0.4)`;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
        e.currentTarget.style.borderColor = tone.border;
        e.currentTarget.style.boxShadow = 'none';
      }}
    >
      {/* Status accent strip */}
      <div
        style={{
          height: 3,
          width: '100%',
          background: tone.accent,
          opacity: isOpen ? 1 : 0.6,
        }}
      />

      <div style={{ padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 10, flex: 1 }}>
        {/* Title + status */}
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'flex-start' }}>
          <div style={{ fontSize: 16, fontWeight: 700, lineHeight: 1.25, letterSpacing: '-0.01em' }}>
            {tournament.name}
          </div>
          <span
            style={{
              fontSize: 10,
              padding: '3px 8px',
              borderRadius: 999,
              background: 'rgba(255,255,255,0.04)',
              color: tone.fg,
              border: `1px solid ${tone.border}`,
              fontFamily: 'Space Mono, monospace',
              letterSpacing: '0.06em',
              textTransform: 'uppercase',
              whiteSpace: 'nowrap',
              flexShrink: 0,
            }}
          >
            {statusLabel[tournament.status]}
          </span>
        </div>

        {/* Meta */}
        <div style={{ fontSize: 12, color: 'var(--cq-text-dim, #7a7d6e)', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          <span>♞ {formatLabel[tournament.format]}</span>
          <span>·</span>
          <span>{tournament.rounds} rondas</span>
          {tournament.timeControl && (
            <>
              <span>·</span>
              <span>⏱ {tournament.timeControl}</span>
            </>
          )}
        </div>

        <div style={{ fontSize: 12, color: 'var(--cq-text-dim, #7a7d6e)' }}>
          📅 {formatDateNice(tournament.startDate)}
          {tournament.endDate && tournament.endDate !== tournament.startDate ? ` → ${formatDateNice(tournament.endDate)}` : ''}
        </div>

        {tournament.location && (
          <div style={{ fontSize: 12, color: 'var(--cq-text-dim, #7a7d6e)' }}>📍 {tournament.location}</div>
        )}

        {/* Plazas */}
        {tournament.maxPlayers ? (
          <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--cq-text-muted, #4a4d40)' }}>
              <span>👥 {tournament.registered ?? 0} / {tournament.maxPlayers} jugadores</span>
              <span style={{ color: tone.fg }}>{Math.round(fillPct)}%</span>
            </div>
            <div
              style={{
                height: 5,
                background: 'rgba(255,255,255,0.05)',
                borderRadius: 999,
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  width: `${fillPct}%`,
                  height: '100%',
                  background: isOpen
                    ? 'linear-gradient(90deg, #6abf74, #3d8a4a)'
                    : tournament.status === 'IN_PROGRESS'
                      ? 'linear-gradient(90deg, #f0b94e, #b88a30)'
                      : 'rgba(255,255,255,0.2)',
                  transition: 'width 0.3s',
                }}
              />
            </div>
          </div>
        ) : null}

        {/* CTA */}
        {isOpen && (
          <div
            style={{
              marginTop: 4,
              padding: '8px 0 0',
              borderTop: '1px solid rgba(255,255,255,0.04)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              fontSize: 12,
              color: '#6abf74',
              fontWeight: 600,
            }}
          >
            <span>Inscribirme</span>
            <span>→</span>
          </div>
        )}
      </div>
    </button>
  );
};
