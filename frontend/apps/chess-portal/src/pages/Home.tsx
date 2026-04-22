import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Input, PlayerCard, Skeleton } from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { playerApi, tournamentApi } from '../api';
import { formatDate, tournamentStatusVariant, unwrapContent } from '../portal-utils';

export const HomePage = () => {
  const navigate = useNavigate();
  const [q, setQ] = useState('');

  const rankings = useQuery({
    queryKey: ['home', 'rankings'],
    queryFn: () => playerApi.rankings(),
  });

  const tournaments = useQuery({
    queryKey: ['home', 'tournaments'],
    queryFn: () => tournamentApi.list({ size: 6 }),
  });

  const onSearch = (event: React.FormEvent) => {
    event.preventDefault();
    if (q.trim()) {
      navigate(`/search?q=${encodeURIComponent(q.trim())}`);
    }
  };

  const topPlayers = (rankings.data ?? []).slice(0, 6);
  const activeTournaments = unwrapContent<Tournament>(tournaments.data).slice(0, 3);

  return (
    <div style={{ padding: 28, display: 'grid', gap: 24 }}>
      <section className="landing-hero board-pattern">
        <div className="hero-grid">
          <div>
            <div className="eyebrow">ChessQuery · Inspirado en Lichess</div>
            <h1 className="page-title">Una plataforma chilena para competir, organizar y sincronizar ajedrez.</h1>
            <p className="page-copy">
              Reorganicé el portal para que hable el lenguaje del tablero: negros profundos, tonos madera,
              indicadores claros y flujos por rol para jugador, organizador y administrador.
            </p>

            <form onSubmit={onSearch} style={{ display: 'flex', gap: 10, marginTop: 20, flexWrap: 'wrap' }}>
              <div style={{ flex: '1 1 320px' }}>
                <Input
                  placeholder="Busca jugadores, clubes o IDs..."
                  value={q}
                  onChange={(event) => setQ(event.target.value)}
                  leftIcon={<span>♞</span>}
                />
              </div>
              <Button type="submit" size="lg">
                Buscar
              </Button>
            </form>

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 18 }}>
              <Badge variant="success" dot>
                Portal jugador
              </Badge>
              <Badge variant="info" dot>
                Gestión organizador
              </Badge>
              <Badge variant="gold" dot>
                ETL / Admin
              </Badge>
            </div>
          </div>

          <div className="hero-callouts">
            <Card className="surface-panel">
              <div className="metric-label">Jugador</div>
              <div style={{ fontSize: 24, fontWeight: 700, marginTop: 8 }}>Emparejamientos, ranking y perfiles.</div>
              <p className="page-copy" style={{ marginTop: 10 }}>
                Navegación enfocada en jugar, consultar rivales y ver competencias activas.
              </p>
              <div style={{ marginTop: 14 }}>
                <Button variant="secondary" onClick={() => navigate('/login?next=/portal')}>
                  Acceso jugador
                </Button>
              </div>
            </Card>
            <Card className="surface-panel">
              <div className="metric-label">Organizador</div>
              <div style={{ fontSize: 24, fontWeight: 700, marginTop: 8 }}>Validación y ciclo de torneos.</div>
              <p className="page-copy" style={{ marginTop: 10 }}>
                Consulta jugadores, revisa standings y gestiona rondas desde una sola mesa.
              </p>
              <div style={{ marginTop: 14 }}>
                <Button variant="secondary" onClick={() => navigate('/login?next=/organizer')}>
                  Acceso organizador
                </Button>
              </div>
            </Card>
          </div>
        </div>
      </section>

      <section className="panel-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
        <Card className="surface-panel">
          <div className="metric-label">Vista separada</div>
          <div style={{ fontSize: 22, fontWeight: 700, marginTop: 10 }}>Jugador</div>
          <p className="page-copy" style={{ marginTop: 10 }}>
            Entrada individual al portal para jugar partidas, consultar jugadores, revisar top 10 y torneos activos.
          </p>
          <div style={{ marginTop: 16 }}>
            <Button onClick={() => navigate('/login?next=/portal')}>Entrar como jugador</Button>
          </div>
        </Card>

        <Card className="surface-panel">
          <div className="metric-label">Vista separada</div>
          <div style={{ fontSize: 22, fontWeight: 700, marginTop: 10 }}>Organizador</div>
          <p className="page-copy" style={{ marginTop: 10 }}>
            Entrada dedicada a validación de perfiles, consulta de jugadores y gestión completa del ciclo de torneos.
          </p>
          <div style={{ marginTop: 16 }}>
            <Button onClick={() => navigate('/login?next=/organizer')}>Entrar como organizador</Button>
          </div>
        </Card>

        <Card className="surface-panel">
          <div className="metric-label">Vista separada</div>
          <div style={{ fontSize: 22, fontWeight: 700, marginTop: 10 }}>Administrador / ETL</div>
          <p className="page-copy" style={{ marginTop: 10 }}>
            Acceso independiente para sincronización de fuentes, estado de breakers y panel operacional del sistema.
          </p>
          <div style={{ marginTop: 16 }}>
            <Button variant="secondary" onClick={() => navigate('/login?next=/admin')}>
              Entrar a admin
            </Button>
          </div>
        </Card>
      </section>

      <div className="panel-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
        <Card
          header={
            <div className="card-header-row">
              <span>Top 10 ranking</span>
              <Button size="sm" variant="ghost" onClick={() => navigate('/rankings')}>
                Ver más
              </Button>
            </div>
          }
        >
          {rankings.isLoading ? (
            <div style={{ display: 'grid', gap: 10 }}>
              {Array.from({ length: 5 }).map((_, index) => (
                <Skeleton key={index} height={88} />
              ))}
            </div>
          ) : rankings.isError ? (
            <ErrorAlert title="No se pudo cargar el ranking" onRetry={() => rankings.refetch()} />
          ) : topPlayers.length === 0 ? (
            <EmptyState title="Aún no hay jugadores rankeados" description="Cuando existan datos nacionales, aparecerán aquí." icon="♟" />
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {topPlayers.map((player) => (
                <PlayerCard key={player.id} player={player} onClick={() => navigate(`/player/${player.id}`)} />
              ))}
            </div>
          )}
        </Card>

        <Card
          header={
            <div className="card-header-row">
              <span>Torneos activos</span>
              <Button size="sm" variant="ghost" onClick={() => navigate('/tournaments')}>
                Ver torneos
              </Button>
            </div>
          }
        >
          {tournaments.isLoading ? (
            <div style={{ display: 'grid', gap: 10 }}>
              {Array.from({ length: 3 }).map((_, index) => (
                <Skeleton key={index} height={112} />
              ))}
            </div>
          ) : tournaments.isError ? (
            <ErrorAlert title="No se pudieron cargar los torneos" onRetry={() => tournaments.refetch()} />
          ) : activeTournaments.length === 0 ? (
            <EmptyState title="Sin competencias visibles" description="Todavía no hay eventos abiertos en el portal." icon="♜" />
          ) : (
            <div style={{ display: 'grid', gap: 12 }}>
              {activeTournaments.map((tournament) => (
                <button
                  key={tournament.id}
                  type="button"
                  className="surface-button"
                  onClick={() => navigate(`/tournaments/${tournament.id}`)}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                    <div style={{ textAlign: 'left' }}>
                      <div style={{ fontSize: 18, fontWeight: 700 }}>{tournament.name}</div>
                      <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                        {formatDate(tournament.startDate)} · {tournament.location ?? 'Ubicación pendiente'}
                      </div>
                    </div>
                    <Badge variant={tournamentStatusVariant(tournament.status)}>{tournament.status}</Badge>
                  </div>
                  <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
                    <Badge variant="neutral">{tournament.format}</Badge>
                    <Badge variant="neutral">{tournament.registered}/{tournament.maxPlayers} jugadores</Badge>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};
