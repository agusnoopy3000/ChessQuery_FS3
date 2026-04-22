import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Input, RatingBadge, Skeleton, Table, TableColumn } from '@chessquery/ui-lib';
import { Player } from '@chessquery/shared';
import { playerApi } from '../api';
import { buildLichessProfileUrl, buildPlayerName, getPrimaryRating } from '../portal-utils';

interface MatchCandidate extends Player {
  ratingGap: number;
}

export const PlayerMatchmakingPage = () => {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');

  const dashboard = useQuery({
    queryKey: ['player', 'matchmaking', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const rankings = useQuery({
    queryKey: ['player', 'matchmaking', 'rankings'],
    queryFn: () => playerApi.rankings(),
  });

  const candidates = useMemo<MatchCandidate[]>(() => {
    if (!dashboard.data) return [];

    const myRating = getPrimaryRating(dashboard.data.profile) ?? 1800;

    return (rankings.data ?? [])
      .filter((player) => player.id !== dashboard.data?.profile.id)
      .filter((player) => {
        const fullName = buildPlayerName(player).toLowerCase();
        return !query.trim() || fullName.includes(query.trim().toLowerCase());
      })
      .map((player) => ({
        ...player,
        ratingGap: Math.abs((getPrimaryRating(player) ?? myRating) - myRating),
      }))
      .sort((a, b) => a.ratingGap - b.ratingGap)
      .slice(0, 12);
  }, [dashboard.data, query, rankings.data]);

  const candidateProfiles = useQuery({
    queryKey: ['player', 'matchmaking', 'profiles', candidates.map((candidate) => candidate.id).join('-')],
    enabled: candidates.length > 0,
    queryFn: async () => {
      const profiles = await Promise.all(candidates.slice(0, 8).map((candidate) => playerApi.publicProfile(candidate.id)));
      return new Map(profiles.map((profile) => [profile.profile.id, profile.profile]));
    },
  });

  if (dashboard.isLoading || rankings.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 16 }}>
        <Skeleton height={160} />
        <Skeleton height={420} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="No se pudo abrir el lobby" onRetry={() => dashboard.refetch()} />
      </div>
    );
  }

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Portal de Juego</div>
          <h1 className="page-title">Emparejamientos sugeridos para lanzar un reto.</h1>
          <p className="page-copy">
            Esta vista ordena posibles rivales por cercanía de rating. Cuando un perfil tenga `lichessUsername`,
            la UI ya queda preparada para saltar a la integración externa o a un flujo de desafío desde el BFF.
          </p>
        </div>
        <div style={{ minWidth: 280 }}>
          <Input
            placeholder="Filtra por nombre..."
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            leftIcon={<span>♞</span>}
          />
        </div>
      </section>

      <Card
        header={
          <div className="card-header-row">
            <span>Lobby competitivo</span>
            <Badge variant="success">Rating match</Badge>
          </div>
        }
      >
        {candidates.length === 0 ? (
          <EmptyState title="Sin rivales sugeridos" description="Ajusta el filtro o vuelve cuando haya más jugadores rankeados." icon="♟" />
        ) : candidateProfiles.isError ? (
          <ErrorAlert
            title="No se pudieron enriquecer los perfiles"
            message="Aun así puedes revisar los jugadores sugeridos."
            onRetry={() => candidateProfiles.refetch()}
          />
        ) : (
          <Table<MatchCandidate>
            rows={candidates}
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
                key: 'source',
                header: 'Perfil',
                render: (row) => {
                  const lichessUsername = candidateProfiles.data?.get(row.id)?.lichessUsername;
                  return lichessUsername ? (
                    <Badge variant="info">{lichessUsername}</Badge>
                  ) : (
                    <Badge variant="neutral">ChessQuery</Badge>
                  );
                },
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
                header: 'Diferencia',
                align: 'right',
                render: (row) => `${row.ratingGap} pts`,
              },
              {
                key: 'availability',
                header: 'Estado',
                render: (row) => {
                  const lichessUsername = candidateProfiles.data?.get(row.id)?.lichessUsername;
                  return lichessUsername ? (
                    <Badge variant="success" dot>
                      Lichess listo
                    </Badge>
                  ) : (
                    <Badge variant="warning" dot>
                      Validar perfil
                    </Badge>
                  );
                },
              },
              {
                key: 'action',
                header: 'Jugar',
                align: 'right',
                render: (row) => {
                  const lichessUsername = candidateProfiles.data?.get(row.id)?.lichessUsername;
                  const link = buildLichessProfileUrl(lichessUsername);

                  return link ? (
                    <a className="btn btn-primary" href={link} target="_blank" rel="noreferrer">
                      Jugar
                    </a>
                  ) : (
                    <Button size="sm" variant="secondary" onClick={() => navigate(`/player/${row.id}`)}>
                      Ver ficha
                    </Button>
                  );
                },
              },
            ] as TableColumn<MatchCandidate>[]}
          />
        )}
      </Card>
    </div>
  );
};
