import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import type { Game } from '@chessquery/shared';
import { playerApi } from '../api';

const fontStack = "'Space Grotesk', system-ui, sans-serif";

/** ELO en chess es siempre entero (1500, 2350, etc.). No abreviar con 'k'. */
const formatElo = (n: number | null | undefined): string => {
  if (n == null) return '—';
  return String(Math.round(n));
};

const formatRelative = (iso: string): string => {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const ms = Date.now() - d.getTime();
  const mins = Math.floor(ms / 60_000);
  if (mins < 60) return `hace ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `hace ${hrs} h`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `hace ${days} d`;
  return d.toLocaleDateString('es-CL', { day: '2-digit', month: 'short' });
};

const resultDelta = (g: Game, myId: number): { sign: '+' | '–' | '='; color: string; label: string } => {
  const isWhite = g.whitePlayerId === myId;
  const winner =
    g.result === '1-0' ? 'white' : g.result === '0-1' ? 'black' : 'draw';
  if (winner === 'draw') return { sign: '=', color: '#7a7d6e', label: 'Tablas' };
  const won = (winner === 'white' && isWhite) || (winner === 'black' && !isWhite);
  return won
    ? { sign: '+', color: '#6abf74', label: 'Victoria' }
    : { sign: '–', color: '#e05a5a', label: 'Derrota' };
};

/* === Mini chart (sparkline) de rating history === */
const RatingSparkline = ({ data }: { data: number[] }) => {
  if (data.length < 2) return null;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = Math.max(max - min, 1);
  const w = 240;
  const h = 56;
  const pad = 4;
  const stepX = (w - pad * 2) / (data.length - 1);
  const points = data
    .map((v, i) => {
      const x = pad + i * stepX;
      const y = h - pad - ((v - min) / range) * (h - pad * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
  return (
    <svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none">
      <defs>
        <linearGradient id="cq-spark-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#6abf74" stopOpacity="0.35" />
          <stop offset="100%" stopColor="#6abf74" stopOpacity="0" />
        </linearGradient>
      </defs>
      <polyline points={`${pad},${h - pad} ${points} ${w - pad},${h - pad}`} fill="url(#cq-spark-grad)" stroke="none" />
      <polyline points={points} fill="none" stroke="#6abf74" strokeWidth="1.5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  );
};

/* === Page === */
export const PlayerPortalPage = () => {
  const navigate = useNavigate();

  const dashboard = useQuery({
    queryKey: ['player', 'portal', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const myId = dashboard.data?.profile?.id;
  const ratingHistory = useQuery({
    queryKey: ['player', 'rating-history', myId],
    queryFn: () => playerApi.ratingHistory(myId!),
    enabled: !!myId,
  });

  const sparkData = useMemo(() => {
    const points = ratingHistory.data ?? [];
    return points.slice(-30).map((p) => p.value);
  }, [ratingHistory.data]);

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16, fontFamily: fontStack, maxWidth: 1100, margin: '0 auto' }}>
        <Skeleton height={180} />
        <Skeleton height={140} />
        <Skeleton height={260} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: 28, fontFamily: fontStack }}>
        <ErrorAlert
          title="No se pudo cargar tu portal"
          message="Verifica la sesión o vuelve a intentar."
          onRetry={() => dashboard.refetch()}
        />
      </div>
    );
  }

  const { profile, stats, recentGames } = dashboard.data;
  const fullName = [profile.firstName, profile.lastName].filter(Boolean).join(' ') || 'Jugador';
  const initials = fullName
    .split(' ')
    .slice(0, 2)
    .map((s) => s[0]?.toUpperCase() ?? '')
    .join('');
  const primaryRating = profile.eloFideStandard ?? profile.eloNational ?? null;
  const ratingLabel = profile.eloFideStandard != null ? 'FIDE' : profile.eloNational != null ? 'NACIONAL' : 'SIN RATING';

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

      {/* === HERO === */}
      <section
        style={{
          background:
            'linear-gradient(135deg, rgba(35,31,26,0.94) 0%, rgba(20,18,16,0.94) 100%)',
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
            Bienvenido de vuelta
          </div>
          <h1
            style={{
              fontSize: 32,
              fontWeight: 700,
              letterSpacing: '-0.02em',
              margin: 0,
              marginBottom: 8,
            }}
          >
            {fullName}
          </h1>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', fontSize: 13, color: 'var(--cq-text-dim, #7a7d6e)' }}>
            {profile.clubName && <span>♜ {profile.clubName}</span>}
            {profile.countryName && (
              <span>
                {profile.countryFlag ?? '🌍'} {profile.countryName}
              </span>
            )}
            {profile.lichessUsername && <span>⚡ @{profile.lichessUsername}</span>}
          </div>
        </div>
        <div
          style={{
            width: 72,
            height: 72,
            borderRadius: 18,
            background: 'rgba(106,191,116,0.12)',
            border: '1px solid rgba(106,191,116,0.4)',
            color: '#6abf74',
            display: 'grid',
            placeItems: 'center',
            fontSize: 26,
            fontWeight: 700,
            fontFamily: fontStack,
          }}
        >
          {initials}
        </div>
      </section>

      {/* === KPI ELO === */}
      <section aria-label="Rating principal">
        <KpiCard
          label="ELO PRINCIPAL"
          subLabel={ratingLabel}
          value={formatElo(primaryRating)}
          highlight
          chart={sparkData.length >= 2 ? <RatingSparkline data={sparkData} /> : null}
        />
      </section>

      {/* === Últimas partidas === */}
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
            <h2 style={{ fontSize: 14, fontWeight: 700, margin: 0 }}>Últimas partidas</h2>
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
              {recentGames.length === 0 ? 'Sin partidas todavía' : `Últimas ${recentGames.length}`}
            </div>
          </div>
          {stats.favoriteOpening && (
            <div style={{ fontSize: 12, color: 'var(--cq-text-dim, #7a7d6e)' }}>
              Apertura favorita: <strong style={{ color: 'var(--cq-text, #e8ead4)' }}>{stats.favoriteOpening}</strong>
            </div>
          )}
        </div>
        {recentGames.length === 0 ? (
          <div style={{ padding: '32px 20px', textAlign: 'center', color: 'var(--cq-text-dim, #7a7d6e)' }}>
            <div style={{ fontSize: 28, marginBottom: 8 }} aria-hidden="true">♞</div>
            <div style={{ fontSize: 14, marginBottom: 12 }}>Sin partidas todavía.</div>
            <button
              onClick={() => navigate('/play')}
              aria-label="Ir a jugar mi primera partida"
              style={{
                background: '#6abf74',
                color: '#0a100a',
                border: 'none',
                padding: '8px 18px',
                borderRadius: 8,
                fontFamily: 'inherit',
                fontWeight: 700,
                fontSize: 13,
                cursor: 'pointer',
              }}
            >
              Jugar mi primera partida →
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {recentGames.slice(0, 6).map((g) => {
              const isWhite = myId != null && g.whitePlayerId === myId;
              const opponent = isWhite ? g.blackName ?? `#${g.blackPlayerId}` : g.whiteName ?? `#${g.whitePlayerId}`;
              const delta = myId != null ? resultDelta(g, myId) : null;
              return (
                <div
                  key={g.id}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '32px 1fr auto auto',
                    gap: 14,
                    alignItems: 'center',
                    padding: '12px 20px',
                    borderBottom: '1px solid rgba(255,255,255,0.04)',
                    fontSize: 13,
                  }}
                >
                  <div style={{ fontSize: 18, opacity: 0.7 }}>{isWhite ? '♔' : '♚'}</div>
                  <div>
                    <div style={{ fontWeight: 600 }}>vs {opponent}</div>
                    <div style={{ fontSize: 11, color: 'var(--cq-text-muted, #4a4d40)', marginTop: 2 }}>
                      {g.openingName ?? 'Sin apertura detectada'} · {formatRelative(g.playedAt)}
                    </div>
                  </div>
                  <div
                    style={{
                      fontSize: 11,
                      padding: '3px 8px',
                      borderRadius: 999,
                      background: g.gameType === 'TOURNAMENT' ? 'rgba(240,185,78,0.12)' : 'rgba(255,255,255,0.04)',
                      color: g.gameType === 'TOURNAMENT' ? '#f0b94e' : 'var(--cq-text-dim, #7a7d6e)',
                      fontFamily: 'Space Mono, monospace',
                      letterSpacing: '0.06em',
                    }}
                  >
                    {g.gameType === 'TOURNAMENT' ? 'TORNEO' : 'CASUAL'}
                  </div>
                  {delta && (
                    <div
                      style={{
                        fontWeight: 700,
                        color: delta.color,
                        fontFamily: 'Space Mono, monospace',
                        fontSize: 13,
                        minWidth: 28,
                        textAlign: 'right',
                      }}
                    >
                      {delta.sign}
                    </div>
                  )}
                </div>
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
  subLabel,
  value,
  highlight,
  chart,
}: {
  label: string;
  subLabel?: string;
  value: string;
  highlight?: boolean;
  chart?: React.ReactNode;
}) => (
  <div
    style={{
      background: highlight
        ? 'linear-gradient(135deg, rgba(106,191,116,0.08) 0%, rgba(20,18,16,0.62) 100%)'
        : 'rgba(20,18,16,0.62)',
      border: `1px solid ${highlight ? 'rgba(106,191,116,0.3)' : 'var(--cq-border, #2a2d27)'}`,
      borderRadius: 14,
      padding: '16px 18px',
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      minHeight: 120,
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
    <div
      style={{
        fontSize: 32,
        fontWeight: 700,
        letterSpacing: '-0.02em',
        color: highlight ? '#6abf74' : 'var(--cq-text, #e8ead4)',
        lineHeight: 1.05,
      }}
    >
      {value}
    </div>
    {subLabel && (
      <div style={{ fontSize: 11, color: 'var(--cq-text-dim, #7a7d6e)', fontFamily: 'Space Mono, monospace', letterSpacing: '0.04em' }}>
        {subLabel}
      </div>
    )}
    {chart && <div style={{ marginTop: 'auto' }}>{chart}</div>}
  </div>
);
