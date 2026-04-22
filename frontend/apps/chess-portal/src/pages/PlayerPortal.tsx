import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, PlayerCard, RatingBadge, Skeleton, Table, TableColumn } from '@chessquery/ui-lib';
import { Player, Tournament } from '@chessquery/shared';
import { playerApi, tournamentApi } from '../api';
import { buildPlayerName, formatDate, formatNumber, getPrimaryRating, tournamentStatusVariant, unwrapContent } from '../portal-utils';

interface SuggestedPairing extends Player {
  ratingGap: number;
}

export const PlayerPortalPage = () => {
  const navigate = useNavigate();

  const dashboard = useQuery({
    queryKey: ['player', 'portal', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const rankings = useQuery({
    queryKey: ['player', 'portal', 'rankings'],
    queryFn: () => playerApi.rankings(),
  });

  const tournaments = useQuery({
    queryKey: ['player', 'portal', 'tournaments'],
    queryFn: () => tournamentApi.list({ size: 8 }),
  });

  const topPlayers = useMemo(() => (rankings.data ?? []).slice(0, 5), [rankings.data]);

  const activeTournaments = useMemo(
    () =>
      unwrapContent<Tournament>(tournaments.data)
        .filter((t) => t.status === 'OPEN' || t.status === 'IN_PROGRESS')
        .slice(0, 4),
    [tournaments.data],
  );

  const suggestedPairings = useMemo<SuggestedPairing[]>(() => {
    if (!dashboard.data) return [];

    const myRating = getPrimaryRating(dashboard.data.profile) ?? 1800;

    return (rankings.data ?? [])
      .filter((player) => player.id !== dashboard.data?.profile.id)
      .map((player) => ({
        ...player,
        ratingGap: Math.abs((getPrimaryRating(player) ?? myRating) - myRating),
      }))
      .sort((a, b) => a.ratingGap - b.ratingGap)
      .slice(0, 6);
  }, [dashboard.data, rankings.data]);

  if (dashboard.isLoading || rankings.isLoading || tournaments.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16 }}>
        <Skeleton height={220} />
        <div style={{ display: 'grid', gridTemplateColumns: '1.1fr 0.9fr', gap: 16 }}>
          <Skeleton height={340} />
          <Skeleton height={340} />
        </div>
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
                  <div className="metric-label">Partidas</div>
                  <div className="metric-value">{formatNumber(stats.totalGames)}</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">Win rate</div>
                  <div className="metric-value">{(stats.winRate * 100).toFixed(1)}%</div>
                </div>
                <div className="metric-card">
                  <div className="metric-label">Lichess</div>
                  <div className="metric-value" style={{ fontSize: 18 }}>
                    {profile.lichessUsername ?? 'Pendiente'}
                  </div>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </section>

      <div className="panel-grid" style={{ gridTemplateColumns: '1.15fr 0.85fr' }}>
        <Card
          header={
            <div className="card-header-row">
              <span>Emparejamientos sugeridos</span>
              <Badge variant="info">Basado en rating cercano</Badge>
            </div>
          }
        >
          {suggestedPairings.length === 0 ? (
            <EmptyState
              title="Aún no hay sugerencias"
              description="Cuando existan más jugadores o tu perfil tenga rating, veremos rivales comparables aquí."
              icon="♞"
            />
          ) : (
            <Table<SuggestedPairing>
              rows={suggestedPairings}
              rowKey={(row) => row.id}
              columns={[
                {
                  key: 'player',
                  header: 'Jugador',
                  render: (row) => (
                    <button className="table-link" onClick={() => navigate(`/player/${row.id}`)}>
                      {buildPlayerName(row)}
                    </button>
                  ),
                },
                {
                  key: 'club',
                  header: 'Club / Región',
                  render: (row) => row.clubName ?? row.countryName ?? '—',
                },
                {
                  key: 'rating',
                  header: 'Rating',
                  align: 'right',
                  render: (row) =>
                    getPrimaryRating(row) != null ? <RatingBadge rating={getPrimaryRating(row)!} label="ELO" /> : '—',
                },
                {
                  key: 'gap',
                  header: 'Dif.',
                  align: 'right',
                  render: (row) => `${row.ratingGap} pts`,
                },
                {
                  key: 'cta',
                  header: 'Acción',
                  align: 'right',
                  render: (row) => (
                    <Button size="sm" variant="secondary" onClick={() => navigate('/play')}>
                      Jugar
                    </Button>
                  ),
                },
              ] as TableColumn<SuggestedPairing>[]}
            />
          )}
        </Card>

        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            header={
              <div className="card-header-row">
                <span>Top 5 nacional</span>
                <Button size="sm" variant="ghost" onClick={() => navigate('/rankings')}>
                  Ver ranking completo
                </Button>
              </div>
            }
          >
            <div style={{ display: 'grid', gap: 10 }}>
              {topPlayers.map((player) => (
                <PlayerCard key={player.id} player={player} onClick={() => navigate(`/player/${player.id}`)} />
              ))}
            </div>
          </Card>

          <Card
            header={
              <div className="card-header-row">
                <span>Torneos activos</span>
                <Button size="sm" variant="ghost" onClick={() => navigate('/tournaments')}>
                  Ver todos
                </Button>
              </div>
            }
          >
            {activeTournaments.length === 0 ? (
              <EmptyState title="No hay competencias abiertas" description="Las próximas rondas aparecerán aquí." icon="♜" />
            ) : (
              <div style={{ display: 'grid', gap: 10 }}>
                {activeTournaments.map((tournament) => (
                  <button
                    key={tournament.id}
                    type="button"
                    className="surface-button"
                    onClick={() => navigate(`/tournaments/${tournament.id}`)}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                      <div style={{ textAlign: 'left' }}>
                        <div style={{ fontWeight: 700 }}>{tournament.name}</div>
                        <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                          {formatDate(tournament.startDate)} · {tournament.location ?? 'Ubicación por confirmar'}
                        </div>
                      </div>
                      <Badge variant={tournamentStatusVariant(tournament.status)}>{tournament.status}</Badge>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};
