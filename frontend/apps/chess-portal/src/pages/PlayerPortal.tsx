import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Card, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { playerApi } from '../api';
import { buildPlayerName, formatNumber, getPrimaryRating } from '../portal-utils';

export const PlayerPortalPage = () => {
  const navigate = useNavigate();

  const dashboard = useQuery({
    queryKey: ['player', 'portal', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16 }}>
        <Skeleton height={220} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert
          title="No se pudo cargar tu portal"
          message="Verifica la sesión o vuelve a intentar en unos segundos."
          onRetry={() => dashboard.refetch()}
        />
      </div>
    );
  }

  const { profile, stats } = dashboard.data;

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="hero-panel board-pattern">
        <div className="hero-grid">
          <div>
            <div className="eyebrow">Chess Portal · Jugador</div>
            <h1 className="page-title">Tu centro de competencia y datos conectados.</h1>
            <p className="page-copy">
              Reunimos ranking, torneos activos, jugadores consultables y una base lista para enlazar retos con
              Lichess desde la capa BFF cuando el backend exponga esa integración.
            </p>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 20 }}>
              <Button size="lg" onClick={() => navigate('/play')}>
                Ver emparejamientos
              </Button>
              <Button size="lg" variant="secondary" onClick={() => navigate('/search')}>
                Consultar jugadores
              </Button>
            </div>
          </div>

          <Card className="surface-panel" style={{ background: 'rgba(10, 10, 10, 0.28)' }}>
            <div style={{ display: 'grid', gap: 16 }}>
              <div>
                <div className="eyebrow">Tu ficha competitiva</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 10 }}>
                  <div className="player-seal">{buildPlayerName(profile).slice(0, 2).toUpperCase()}</div>
                  <div>
                    <div style={{ fontSize: 22, fontWeight: 700 }}>{buildPlayerName(profile)}</div>
                    <div style={{ color: 'var(--text-muted)' }}>
                      {profile.clubName ?? profile.countryName ?? 'Jugador ChessQuery'}
                    </div>
                  </div>
                </div>
              </div>

              <div className="metric-grid">
                <div className="metric-card">
                  <div className="metric-label">Rating principal</div>
                  <div className="metric-value">{formatNumber(getPrimaryRating(profile))}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">Win rate</div>
                  <div className="metric-value">{(stats.winRate * 100).toFixed(1)}%</div>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </section>
    </div>
  );
};
