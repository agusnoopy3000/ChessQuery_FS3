import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { useAuth } from '@chessquery/shared';
import type { Tournament } from '@chessquery/shared';
import { organizerApi, type CreateTournamentInput } from '../api';
import { dedupeBy, formatDate, tournamentStatusVariant, unwrapContent } from '../portal-utils';
import { CreateTournamentModal } from '../components/CreateTournamentModal';

const fontStack = "'Space Grotesk', system-ui, sans-serif";

const statusLabel: Record<Tournament['status'], string> = {
  DRAFT: 'Borrador',
  OPEN: 'Inscripciones',
  IN_PROGRESS: 'En curso',
  FINISHED: 'Finalizado',
};

const statusBadgeColor: Record<Tournament['status'], { bg: string; fg: string; border: string }> = {
  DRAFT: { bg: 'rgba(255,255,255,0.04)', fg: '#7a7d6e', border: 'rgba(255,255,255,0.08)' },
  OPEN: { bg: 'rgba(106,191,116,0.12)', fg: '#6abf74', border: 'rgba(106,191,116,0.4)' },
  IN_PROGRESS: { bg: 'rgba(240,185,78,0.12)', fg: '#f0b94e', border: 'rgba(240,185,78,0.4)' },
  FINISHED: { bg: 'rgba(122,125,110,0.12)', fg: '#7a7d6e', border: 'rgba(122,125,110,0.4)' },
};

export const OrganizerPortalPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [showCreateModal, setShowCreateModal] = useState(false);

  const tournaments = useQuery({
    queryKey: ['organizer', 'portal', 'tournaments'],
    queryFn: () => organizerApi.listTournaments({ size: 12 }),
  });

  const createTournament = useMutation({
    mutationFn: (input: CreateTournamentInput) => organizerApi.createTournament(input),
    onSuccess: async (created) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'portal', 'tournaments'] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] }),
      ]);
      setShowCreateModal(false);
      navigate(`/tournaments?selected=${created.id}`);
    },
  });

  const items = useMemo(
    () => dedupeBy(unwrapContent<Tournament>(tournaments.data), (t) => t.id),
    [tournaments.data],
  );

  const kpis = useMemo(() => {
    const total = items.length;
    const open = items.filter((t) => t.status === 'OPEN').length;
    const inProgress = items.filter((t) => t.status === 'IN_PROGRESS').length;
    const finished = items.filter((t) => t.status === 'FINISHED').length;
    const pendingApprovals = items.reduce((sum, t) => sum + (t.pending ?? 0), 0);
    return { total, open, inProgress, finished, pendingApprovals };
  }, [items]);

  const recent = items.slice(0, 5);
  const organizerName = user?.email?.split('@')[0] ?? 'Organizador';

  return (
    <div
      style={{
        padding: 28,
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
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
      `}</style>

      <CreateTournamentModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSubmit={(input) => createTournament.mutate(input)}
        loading={createTournament.isPending}
        error={
          (createTournament.error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ??
          (createTournament.error as { message?: string })?.message ??
          null
        }
      />

      {/* === HERO === */}
      <section
        style={{
          background: 'linear-gradient(135deg, rgba(35,31,26,0.94) 0%, rgba(20,18,16,0.94) 100%)',
          border: '1px solid var(--cq-border, #2a2d27)',
          borderRadius: 18,
          padding: '28px 32px',
          display: 'grid',
          gridTemplateColumns: '1fr auto',
          gap: 24,
          alignItems: 'center',
        }}
      >
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
            Mesa de control
          </div>
          <h1 style={{ fontSize: 30, fontWeight: 700, letterSpacing: '-0.02em', margin: 0, marginBottom: 8 }}>
            Hola, {organizerName} 👑
          </h1>
          <p style={{ fontSize: 14, color: 'var(--cq-text-dim, #7a7d6e)', margin: 0, lineHeight: 1.6, maxWidth: 560 }}>
            Crea torneos, valida inscripciones y genera rondas desde un solo lugar.
            {kpis.pendingApprovals > 0 && (
              <>
                {' '}
                Tienes{' '}
                <strong style={{ color: '#f0b94e' }}>
                  {kpis.pendingApprovals} inscripción{kpis.pendingApprovals === 1 ? '' : 'es'} pendiente{kpis.pendingApprovals === 1 ? '' : 's'}
                </strong>{' '}
                por revisar.
              </>
            )}
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          style={{
            background: 'linear-gradient(135deg, #6abf74 0%, #3d8a4a 100%)',
            color: '#0a100a',
            border: 'none',
            padding: '14px 24px',
            borderRadius: 12,
            fontFamily: 'inherit',
            fontWeight: 700,
            fontSize: 15,
            cursor: 'pointer',
            boxShadow: '0 8px 24px rgba(106,191,116,0.25)',
            transition: 'transform 0.15s ease',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.transform = 'translateY(-2px)')}
          onMouseLeave={(e) => (e.currentTarget.style.transform = 'translateY(0)')}
        >
          + Crear torneo
        </button>
      </section>

      {/* === KPIs === */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
        <KpiCard
          label="TOTAL TORNEOS"
          value={String(kpis.total)}
          subLabel="Creados por ti"
          tone="default"
          onClick={() => navigate('/tournaments')}
        />
        <KpiCard
          label="INSCRIPCIONES"
          value={String(kpis.pendingApprovals)}
          subLabel="Pendientes de aprobación"
          tone={kpis.pendingApprovals > 0 ? 'warning' : 'default'}
          onClick={() => navigate('/tournaments')}
        />
        <KpiCard label="ABIERTOS" value={String(kpis.open)} subLabel="Aceptando jugadores" tone="success" />
        <KpiCard label="EN CURSO" value={String(kpis.inProgress)} subLabel="Rondas activas" tone="info" />
        <KpiCard label="FINALIZADOS" value={String(kpis.finished)} subLabel="Histórico" tone="default" />
      </section>

      {/* === Recent tournaments === */}
      <section
        style={{
          background: 'rgba(20,18,16,0.62)',
          border: '1px solid var(--cq-border, #2a2d27)',
          borderRadius: 18,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: '14px 20px',
            borderBottom: '1px solid var(--cq-border, #2a2d27)',
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <div style={{ fontSize: 14, fontWeight: 700 }}>Tus torneos recientes</div>
            <div
              style={{
                fontSize: 11,
                letterSpacing: '0.1em',
                textTransform: 'uppercase',
                color: 'var(--cq-text-muted, #4a4d40)',
                fontFamily: 'Space Mono, monospace',
                marginTop: 2,
              }}
            >
              {items.length === 0 ? 'Sin torneos aún' : `${items.length} torneo${items.length === 1 ? '' : 's'} en total`}
            </div>
          </div>
          {items.length > 0 && (
            <button
              onClick={() => navigate('/tournaments')}
              style={{
                background: 'transparent',
                border: '1px solid var(--cq-border, #2a2d27)',
                color: 'var(--cq-text-dim, #7a7d6e)',
                padding: '6px 12px',
                borderRadius: 8,
                fontFamily: 'inherit',
                fontSize: 12,
                cursor: 'pointer',
                transition: 'border-color 0.15s, color 0.15s',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = 'rgba(106,191,116,0.4)';
                e.currentTarget.style.color = '#6abf74';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = 'var(--cq-border, #2a2d27)';
                e.currentTarget.style.color = 'var(--cq-text-dim, #7a7d6e)';
              }}
            >
              Ver todos →
            </button>
          )}
        </div>

        {tournaments.isLoading ? (
          <div style={{ padding: 16, display: 'grid', gap: 10 }}>
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} height={70} />
            ))}
          </div>
        ) : tournaments.isError ? (
          <div style={{ padding: 20 }}>
            <ErrorAlert title="No se pudieron cargar los torneos" onRetry={() => tournaments.refetch()} />
          </div>
        ) : recent.length === 0 ? (
          <div style={{ padding: '40px 20px', textAlign: 'center', color: 'var(--cq-text-dim, #7a7d6e)' }}>
            <div style={{ fontSize: 32, marginBottom: 10 }}>♜</div>
            <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--cq-text, #e8ead4)', marginBottom: 6 }}>
              Aún no has creado torneos
            </div>
            <div style={{ fontSize: 13, marginBottom: 18 }}>
              Empieza tu primer torneo y los jugadores podrán inscribirse desde su portal.
            </div>
            <button
              onClick={() => setShowCreateModal(true)}
              style={{
                background: '#6abf74',
                color: '#0a100a',
                border: 'none',
                padding: '10px 20px',
                borderRadius: 8,
                fontFamily: 'inherit',
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              + Crear primer torneo
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {recent.map((t) => {
              const colors = statusBadgeColor[t.status];
              const fillPct = t.maxPlayers ? Math.min(100, ((t.registered ?? 0) / t.maxPlayers) * 100) : 0;
              return (
                <button
                  key={t.id}
                  onClick={() => navigate('/tournaments')}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr auto',
                    gap: 14,
                    alignItems: 'center',
                    padding: '14px 20px',
                    borderBottom: '1px solid rgba(255,255,255,0.04)',
                    background: 'transparent',
                    border: 'none',
                    borderTop: 'none',
                    color: 'var(--cq-text, #e8ead4)',
                    fontFamily: 'inherit',
                    cursor: 'pointer',
                    textAlign: 'left',
                    transition: 'background 0.15s',
                  }}
                  onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(255,255,255,0.025)')}
                  onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 15, fontWeight: 600 }}>{t.name}</span>
                      <span
                        style={{
                          fontSize: 10,
                          padding: '2px 8px',
                          borderRadius: 999,
                          background: colors.bg,
                          color: colors.fg,
                          border: `1px solid ${colors.border}`,
                          fontFamily: 'Space Mono, monospace',
                          letterSpacing: '0.06em',
                          textTransform: 'uppercase',
                        }}
                      >
                        {statusLabel[t.status]}
                      </span>
                      {(t.pending ?? 0) > 0 && (
                        <span
                          style={{
                            fontSize: 10,
                            padding: '2px 8px',
                            borderRadius: 999,
                            background: 'rgba(240,185,78,0.12)',
                            color: '#f0b94e',
                            border: '1px solid rgba(240,185,78,0.4)',
                            fontFamily: 'Space Mono, monospace',
                            letterSpacing: '0.06em',
                          }}
                        >
                          {t.pending} POR APROBAR
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--cq-text-muted, #4a4d40)' }}>
                      {t.format} · {t.rounds} rondas · {formatDate(t.startDate)}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11, color: 'var(--cq-text-dim, #7a7d6e)' }}>
                      <span>
                        {t.registered ?? 0}/{t.maxPlayers || '∞'} jugadores
                      </span>
                      {t.maxPlayers ? (
                        <div
                          style={{
                            flex: 1,
                            maxWidth: 120,
                            height: 4,
                            background: 'rgba(255,255,255,0.05)',
                            borderRadius: 999,
                            overflow: 'hidden',
                          }}
                        >
                          <div
                            style={{
                              width: `${fillPct}%`,
                              height: '100%',
                              background: 'linear-gradient(90deg, #6abf74, #3d8a4a)',
                              transition: 'width 0.3s',
                            }}
                          />
                        </div>
                      ) : null}
                    </div>
                  </div>
                  <span style={{ color: 'var(--cq-text-muted, #4a4d40)' }}>→</span>
                </button>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
};

const KpiCard = ({
  label,
  value,
  subLabel,
  tone = 'default',
  onClick,
}: {
  label: string;
  value: string;
  subLabel?: string;
  tone?: 'default' | 'success' | 'warning' | 'info';
  onClick?: () => void;
}) => {
  const palette = {
    default: { fg: 'var(--cq-text, #e8ead4)', accent: 'var(--cq-text-dim, #7a7d6e)', border: 'var(--cq-border, #2a2d27)', bg: 'rgba(20,18,16,0.62)' },
    success: { fg: '#6abf74', accent: '#6abf74', border: 'rgba(106,191,116,0.3)', bg: 'rgba(106,191,116,0.06)' },
    warning: { fg: '#f0b94e', accent: '#f0b94e', border: 'rgba(240,185,78,0.3)', bg: 'rgba(240,185,78,0.06)' },
    info:    { fg: '#7ec3ff', accent: '#7ec3ff', border: 'rgba(126,195,255,0.3)', bg: 'rgba(126,195,255,0.06)' },
  }[tone];

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={!onClick}
      style={{
        background: palette.bg,
        border: `1px solid ${palette.border}`,
        borderRadius: 14,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        textAlign: 'left',
        cursor: onClick ? 'pointer' : 'default',
        fontFamily: 'inherit',
        color: 'var(--cq-text, #e8ead4)',
        transition: 'transform 0.15s ease, border-color 0.15s ease',
      }}
      onMouseEnter={(e) => {
        if (onClick) e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.transform = 'translateY(0)';
      }}
    >
      <div
        style={{
          fontSize: 10,
          letterSpacing: '0.12em',
          color: 'var(--cq-text-muted, #4a4d40)',
          fontFamily: 'Space Mono, monospace',
        }}
      >
        {label}
      </div>
      <div style={{ fontSize: 28, fontWeight: 700, letterSpacing: '-0.02em', color: palette.fg }}>{value}</div>
      {subLabel && (
        <div style={{ fontSize: 11, color: 'var(--cq-text-dim, #7a7d6e)' }}>{subLabel}</div>
      )}
    </button>
  );
};
