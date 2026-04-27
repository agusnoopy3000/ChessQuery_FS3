import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Skeleton } from '@chessquery/ui-lib';
import { Tournament } from '@chessquery/shared';
import { organizerApi } from '../api';
import { formatDate, formatNumber, tournamentStatusVariant, unwrapContent } from '../portal-utils';

export const OrganizerPortalPage = () => {
  const navigate = useNavigate();

  const tournaments = useQuery({
    queryKey: ['organizer', 'portal', 'tournaments'],
    queryFn: () => organizerApi.listTournaments({ size: 12 }),
  });

  const summary = useMemo(() => {
    const items = unwrapContent<Tournament>(tournaments.data);
    return {
      created: items.length,
      inProgress: items.filter((item) => item.status === 'IN_PROGRESS').length,
      finished: items.filter((item) => item.status === 'FINISHED').length,
      recent: items.slice(0, 6),
    };
  }, [tournaments.data]);

  if (tournaments.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16 }}>
        <Skeleton height={220} />
        <Skeleton height={380} />
      </div>
    );
  }

  if (tournaments.isError) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="No se pudo cargar el portal del organizador" onRetry={() => tournaments.refetch()} />
      </div>
    );
  }

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="hero-panel organizer-banner">
        <div className="hero-grid">
          <div>
            <div className="eyebrow">Organizer Workspace</div>
            <h1 className="page-title">Controla el ciclo de tus torneos desde una sola vista.</h1>
            <p className="page-copy">
              Aquí concentramos consulta de jugadores, seguimiento competitivo, standings y gestión operativa inspirada
              en el ritmo visual de Lichess: mucho contraste, lectura rápida y foco en el tablero.
            </p>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 20 }}>
              <Button size="lg" onClick={() => navigate('/organizer/tournaments')}>
                Gestionar torneos
              </Button>
              <Button size="lg" variant="secondary" onClick={() => navigate('/organizer/players')}>
                Validar jugadores
              </Button>
            </div>
          </div>

          <Card className="surface-panel">
            <div className="metric-grid">
              <div className="metric-card">
                <div className="metric-label">Torneos creados</div>
                <div className="metric-value">{formatNumber(summary.created)}</div>
              </div>
              <div className="metric-card">
                <div className="metric-label">En curso</div>
                <div className="metric-value">{formatNumber(summary.inProgress)}</div>
              </div>
              <div className="metric-card">
                <div className="metric-label">Cerrados</div>
                <div className="metric-value">{formatNumber(summary.finished)}</div>
              </div>
            </div>
          </Card>
        </div>
      </section>

      <Card
        header={
          <div className="card-header-row">
            <span>Torneos recientemente creados</span>
            <Button size="sm" variant="ghost" onClick={() => navigate('/organizer/tournaments')}>
              Ver todos
            </Button>
          </div>
        }
      >
        {summary.recent.length === 0 ? (
          <EmptyState title="Aún no hay torneos registrados" description="Cuando el BFF entregue tus eventos, aparecerán aquí con su seguimiento." icon="♜" />
        ) : (
          <div style={{ display: 'grid', gap: 12 }}>
            {summary.recent.map((tournament) => (
              <button
                key={tournament.id}
                type="button"
                className="surface-button"
                onClick={() => navigate('/organizer/tournaments')}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                  <div style={{ textAlign: 'left' }}>
                    <div style={{ fontWeight: 700 }}>{tournament.name}</div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {formatDate(tournament.startDate)} · {tournament.format} · {tournament.registered}/{tournament.maxPlayers}
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
  );
};
