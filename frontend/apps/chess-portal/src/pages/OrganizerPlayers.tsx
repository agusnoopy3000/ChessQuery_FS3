import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Input, PlayerCard, RatingBadge, Skeleton } from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { organizerApi, playerApi } from '../api';
import { buildLichessProfileUrl, buildPlayerName, getPrimaryRating, unwrapContent } from '../portal-utils';

export const OrganizerPlayersPage = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('ro');
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const results = useQuery({
    queryKey: ['organizer', 'players', 'search', search],
    queryFn: () => playerApi.search(search),
    enabled: search.trim().length >= 2,
  });

  useEffect(() => {
    if (!selectedId && results.data?.[0]) {
      setSelectedId(results.data[0].id);
    }
  }, [results.data, selectedId]);

  const selectedProfile = useQuery({
    queryKey: ['organizer', 'players', 'profile', selectedId],
    queryFn: () => playerApi.publicProfile(selectedId!),
    enabled: selectedId != null,
  });

  const tournaments = useQuery({
    queryKey: ['organizer', 'players', 'tournaments'],
    queryFn: () => organizerApi.listTournaments({ size: 8 }),
  });

  const activeTournaments = useMemo(
    () =>
      unwrapContent<Tournament>(tournaments.data)
        .filter((tournament) => tournament.status !== 'FINISHED')
        .slice(0, 4),
    [tournaments.data],
  );

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Validación de jugadores</div>
          <h1 className="page-title">Consulta perfiles antes de admitirlos o sembrarlos en un torneo.</h1>
          <p className="page-copy">
            Esta vista junta búsqueda, rating, trazas recientes y presencia Lichess para que la validación sea mucho
            más rápida desde la mesa del organizador.
          </p>
        </div>
        <div style={{ minWidth: 320 }}>
          <Input
            placeholder="Busca nombre, apellido o FIDE..."
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            leftIcon={<span>♟</span>}
          />
        </div>
      </section>

      <div className="panel-grid" style={{ gridTemplateColumns: '0.9fr 1.1fr' }}>
        <Card
          header={
            <div className="card-header-row">
              <span>Resultados</span>
              <Badge variant="info">{results.data?.length ?? 0} perfiles</Badge>
            </div>
          }
        >
          {results.isLoading ? (
            <div style={{ display: 'grid', gap: 10 }}>
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} height={90} />
              ))}
            </div>
          ) : results.isError ? (
            <ErrorAlert title="No se pudo consultar jugadores" onRetry={() => results.refetch()} />
          ) : !results.data || results.data.length === 0 ? (
            <EmptyState title="Sin resultados" description="Usa al menos 2 caracteres para encontrar jugadores." icon="♘" />
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {results.data.map((player) => (
                <div key={player.id} style={{ position: 'relative' }}>
                  {selectedId === player.id && <div className="selection-rail" />}
                  <PlayerCard player={player} onClick={() => setSelectedId(player.id)} ratingLabel="Seed" />
                </div>
              ))}
            </div>
          )}
        </Card>

        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            header={
              <div className="card-header-row">
                <span>Perfil elegido</span>
                {selectedProfile.data?.profile.lichessUsername ? (
                  <a
                    className="btn btn-secondary"
                    href={buildLichessProfileUrl(selectedProfile.data.profile.lichessUsername) ?? '#'}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Ver Lichess
                  </a>
                ) : null}
              </div>
            }
          >
            {selectedProfile.isLoading ? (
              <Skeleton height={260} />
            ) : selectedProfile.isError || !selectedProfile.data ? (
              <EmptyState title="Selecciona un jugador" description="El detalle del perfil aparecerá aquí." icon="♔" />
            ) : (
              <div style={{ display: 'grid', gap: 16 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                  <div>
                    <div className="eyebrow">Perfil</div>
                    <div style={{ fontSize: 24, fontWeight: 700 }}>{buildPlayerName(selectedProfile.data.profile)}</div>
                    <div style={{ color: 'var(--text-muted)' }}>
                      {selectedProfile.data.profile.clubName ?? selectedProfile.data.profile.countryName ?? 'ChessQuery'}
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {getPrimaryRating(selectedProfile.data.profile) != null ? (
                      <RatingBadge rating={getPrimaryRating(selectedProfile.data.profile)!} label="ELO" />
                    ) : null}
                    {selectedProfile.data.profile.fideTitle ? (
                      <Badge variant="gold">{selectedProfile.data.profile.fideTitle}</Badge>
                    ) : null}
                  </div>
                </div>

                <div className="metric-grid">
                  <div className="metric-card">
                    <div className="metric-label">Lichess</div>
                    <div className="metric-value" style={{ fontSize: 18 }}>
                      {selectedProfile.data.profile.lichessUsername ?? 'No vinculado'}
                    </div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Partidas</div>
                    <div className="metric-value">{selectedProfile.data.stats.totalGames}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Victorias</div>
                    <div className="metric-value">{selectedProfile.data.stats.wins}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Win rate</div>
                    <div className="metric-value">{(selectedProfile.data.stats.winRate * 100).toFixed(1)}%</div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                  <Button variant="secondary" onClick={() => navigate(`/player/${selectedProfile.data.profile.id}`)}>
                    Ver ficha pública
                  </Button>
                  <Button onClick={() => navigate('/organizer/tournaments')}>Usar en torneo</Button>
                </div>
              </div>
            )}
          </Card>

          <Card
            header={
              <div className="card-header-row">
                <span>Torneos para validar</span>
                <Button size="sm" variant="ghost" onClick={() => navigate('/organizer/tournaments')}>
                  Abrir gestión
                </Button>
              </div>
            }
          >
            {activeTournaments.length === 0 ? (
              <EmptyState title="Sin torneos activos" description="Cuando existan eventos abiertos, aparecerán como referencia de validación." icon="♜" />
            ) : (
              <div style={{ display: 'grid', gap: 10 }}>
                {activeTournaments.map((tournament) => (
                  <button key={tournament.id} type="button" className="surface-button" onClick={() => navigate('/organizer/tournaments')}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center' }}>
                      <div style={{ textAlign: 'left' }}>
                        <div style={{ fontWeight: 700 }}>{tournament.name}</div>
                        <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                          {tournament.format} · {tournament.registered}/{tournament.maxPlayers}
                        </div>
                      </div>
                      <Badge variant="info">{tournament.status}</Badge>
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
