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
};

const PRIMARY_VARIANTS = ['bullet', 'blitz', 'rapid', 'classical', 'correspondence'];

export const MyDashboardPage = () => {
  const dashboard = useQuery({
    queryKey: ['me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const lichess = useQuery({
    queryKey: ['me', 'lichess'],
    queryFn: () => playerApi.lichess(String(dashboard.data!.profile.id)),
    enabled: !!dashboard.data?.profile.lichessUsername,
    retry: false,
  });

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={100} />
        <Skeleton height={220} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert
          title="No se pudo cargar tu dashboard"
          onRetry={() => dashboard.refetch()}
        />
      </div>
    );
  }

  const { profile: p } = dashboard.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Jugador';

  const ratings = (lichess.data?.ratings ?? []) as LichessRating[];
  const platformRatings = ratings.filter((r) => PRIMARY_VARIANTS.includes(r.variant));
  const platformBest = platformRatings.length > 0
    ? Math.max(...platformRatings.map((r) => r.rating ?? 0))
    : null;

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 980, margin: '0 auto' }}>
      <div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Bienvenido de vuelta</div>
        <h1 style={{ fontSize: 26, fontWeight: 700 }}>{fullName}</h1>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: 12,
        }}
      >
        <Card header="Nombre completo">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{fullName}</div>
        </Card>
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
          <div style={{ fontSize: 18, fontWeight: 600 }}>{p.clubName ?? '—'}</div>
        </Card>
      </div>

      <Card header={`Resumen Lichess${p.lichessUsername ? ` · @${p.lichessUsername}` : ''}`}>
        {!p.lichessUsername ? (
          <EmptyState
            title="Sin cuenta Lichess vinculada"
            description="Ingresa tu usuario de Lichess en el registro para ver el resumen."
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
          <EmptyState title="Sin ratings disponibles" description="No tienes partidas rateadas." />
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
