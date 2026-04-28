import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
} from '@chessquery/ui-lib';
import { playerApi } from '../api';

interface LichessRating {
  variant: string;
  rating: number | null;
  games: number | null;
  prog: number | null;
}

const VARIANT_LABEL: Record<string, string> = {
  bullet: 'Bullet',
  blitz: 'Blitz',
  rapid: 'Rapid',
  classical: 'Classical',
  correspondence: 'Correspondence',
  ultraBullet: 'UltraBullet',
  chess960: '960',
  kingOfTheHill: 'KOTH',
  threeCheck: '3-Check',
  antichess: 'Antichess',
  atomic: 'Atomic',
  horde: 'Horde',
  racingKings: 'RKings',
  crazyhouse: 'Crazyhouse',
  puzzle: 'Puzzle',
};

const PRIMARY_VARIANTS = ['bullet', 'blitz', 'rapid', 'classical', 'correspondence'];

export const PlayerProfilePage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const profile = useQuery({
    queryKey: ['player', id, 'profile'],
    queryFn: () => playerApi.publicProfile(id!),
    enabled: !!id,
  });

  const lichess = useQuery({
    queryKey: ['player', id, 'lichess'],
    queryFn: () => playerApi.lichess(id!),
    enabled: !!id && !!profile.data?.profile.lichessUsername,
    retry: false,
  });

  if (profile.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={120} />
        <Skeleton height={140} />
        <Skeleton height={220} />
      </div>
    );
  }

  if (profile.isError || !profile.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert
          title="Jugador no disponible"
          message="No se pudo cargar el perfil del jugador."
          onRetry={() => profile.refetch()}
        />
      </div>
    );
  }

  const { profile: p } = profile.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || '—';
  const initials = (p.firstName?.[0] ?? '?') + (p.lastName?.[0] ?? '');

  const ratings = (lichess.data?.ratings ?? []) as LichessRating[];
  const platformRatings = ratings.filter((r) => PRIMARY_VARIANTS.includes(r.variant));
  const platformBest = platformRatings.length > 0
    ? Math.max(...platformRatings.map((r) => r.rating ?? 0))
    : null;

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 980, margin: '0 auto' }}>
      <button className="btn btn-ghost" style={{ alignSelf: 'flex-start' }} onClick={() => navigate(-1)}>
        ← Volver
      </button>

      {/* Header card */}
      <Card padded>
        <div style={{ display: 'flex', gap: 22, alignItems: 'center', flexWrap: 'wrap' }}>
          <div
            style={{
              width: 88,
              height: 88,
              borderRadius: '50%',
              background: 'var(--accent-dim)',
              border: '2px solid oklch(68% 0.20 150 / 0.35)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 32,
              color: 'var(--accent)',
              fontFamily: "'Space Grotesk', system-ui, sans-serif",
              fontWeight: 700,
              flexShrink: 0,
            }}
          >
            {initials.toUpperCase()}
          </div>
          <div style={{ flex: 1, minWidth: 200 }}>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui, sans-serif",
                fontSize: 28,
                fontWeight: 700,
                lineHeight: 1.1,
              }}
            >
              {fullName}
            </div>
          </div>
        </div>
      </Card>

      {/* Hero stats: ELOs y Club */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 12,
        }}
      >
        <Card header="ELO Nacional">
          {p.eloNational != null ? (
            <RatingBadge rating={p.eloNational} label="NAC" />
          ) : (
            <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>
          )}
        </Card>
        <Card header="ELO Internacional">
          {p.eloFideStandard != null ? (
            <RatingBadge rating={p.eloFideStandard} label="FIDE" />
          ) : (
            <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>
          )}
        </Card>
        <Card header="ELO Plataforma">
          {platformBest != null ? (
            <div>
              <RatingBadge rating={platformBest} label="LICHESS" />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
                Mejor variante
              </div>
            </div>
          ) : lichess.isLoading ? (
            <Skeleton height={26} width={90} />
          ) : (
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
              {p.lichessUsername ? 'Sin datos en Lichess' : 'Sin cuenta vinculada'}
            </div>
          )}
        </Card>
        <Card header="Club">
          <div
            style={{
              fontFamily: "'Space Grotesk', system-ui, sans-serif",
              fontSize: 16,
              fontWeight: 600,
              lineHeight: 1.3,
            }}
          >
            {p.clubName ?? '—'}
          </div>
        </Card>
      </div>

      {/* Lichess breakdown por modalidad */}
      <Card header={`Resumen Lichess${p.lichessUsername ? ` · @${p.lichessUsername}` : ''}`}>
        {!p.lichessUsername ? (
          <EmptyState
            title="Sin cuenta Lichess vinculada"
            description="Este jugador no proporcionó su usuario de Lichess durante el registro."
            icon="♖"
          />
        ) : lichess.isLoading ? (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} height={86} />)}
          </div>
        ) : lichess.isError ? (
          <ErrorAlert title="No se pudo consultar Lichess" onRetry={() => lichess.refetch()} />
        ) : !lichess.data?.found ? (
          <EmptyState
            title={`Usuario @${p.lichessUsername} no encontrado en Lichess`}
            description={lichess.data?.error ?? 'La API Lichess devolvió 404.'}
            icon="⚠"
          />
        ) : platformRatings.length === 0 ? (
          <EmptyState title="Sin ratings disponibles" description="El usuario no tiene partidas rateadas." />
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: 10,
            }}
          >
            {platformRatings.map((r) => (
              <div
                key={r.variant}
                style={{
                  padding: '14px 16px',
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                }}
              >
                <div
                  style={{
                    fontSize: 11,
                    color: 'var(--text-muted)',
                    textTransform: 'uppercase',
                    letterSpacing: 0.5,
                    fontWeight: 600,
                  }}
                >
                  {VARIANT_LABEL[r.variant] ?? r.variant}
                </div>
                <div
                  style={{
                    fontFamily: "'Space Grotesk', system-ui, sans-serif",
                    fontSize: 26,
                    fontWeight: 700,
                    marginTop: 2,
                  }}
                >
                  {r.rating ?? '—'}
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                  {r.games ?? 0} partidas
                  {r.prog != null && r.prog !== 0 ? (
                    <span style={{ marginLeft: 6, color: r.prog > 0 ? 'var(--accent)' : 'var(--red)' }}>
                      {r.prog > 0 ? '+' : ''}{r.prog}
                    </span>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};
