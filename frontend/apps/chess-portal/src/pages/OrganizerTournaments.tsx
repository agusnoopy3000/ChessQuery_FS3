import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Skeleton, StandingsTable, Table, TableColumn } from '@chessquery/ui-lib';
import { Pairing, Tournament } from '@chessquery/shared';
import { organizerApi } from '../api';
import { formatDate, tournamentStatusVariant, unwrapContent } from '../portal-utils';

const RESULT_OPTIONS = [
  { value: '1-0', label: '1-0' },
  { value: '0-1', label: '0-1' },
  { value: '1/2-1/2', label: '1/2-1/2' },
];

export const OrganizerTournamentsPage = () => {
  const queryClient = useQueryClient();
  const [selectedTournamentId, setSelectedTournamentId] = useState<number | null>(null);
  const [selectedRound, setSelectedRound] = useState(1);
  const [draftResults, setDraftResults] = useState<Record<number, string>>({});

  const tournaments = useQuery({
    queryKey: ['organizer', 'tournaments', 'list'],
    queryFn: () => organizerApi.listTournaments({ size: 20 }),
  });

  const tournamentRows = useMemo(() => unwrapContent<Tournament>(tournaments.data), [tournaments.data]);

  useEffect(() => {
    if (!selectedTournamentId && tournamentRows[0]) {
      setSelectedTournamentId(tournamentRows[0].id);
    }
  }, [selectedTournamentId, tournamentRows]);

  const selectedTournament = useMemo<Tournament | undefined>(
    () => tournamentRows.find((tournament) => tournament.id === selectedTournamentId),
    [selectedTournamentId, tournamentRows],
  );

  useEffect(() => {
    setSelectedRound(1);
    setDraftResults({});
  }, [selectedTournamentId]);

  const standings = useQuery({
    queryKey: ['organizer', 'tournaments', 'standings', selectedTournamentId],
    queryFn: () => organizerApi.tournamentStandings(selectedTournamentId!),
    enabled: selectedTournamentId != null,
  });

  const round = useQuery({
    queryKey: ['organizer', 'tournaments', 'round', selectedTournamentId, selectedRound],
    queryFn: () => organizerApi.round(selectedTournamentId!, selectedRound),
    enabled: selectedTournamentId != null,
  });

  const generateRound = useMutation({
    mutationFn: () => organizerApi.generateRound(selectedTournamentId!, selectedRound),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'round', selectedTournamentId, selectedRound] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'standings', selectedTournamentId] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] }),
      ]);
    },
  });

  const patchResult = useMutation({
    mutationFn: ({ pairingId, result }: { pairingId: number; result: string }) =>
      organizerApi.patchPairingResult(pairingId, result),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'round', selectedTournamentId, selectedRound] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'standings', selectedTournamentId] }),
      ]);
    },
  });

  const kpis = useMemo(
    () => ({
      total: tournamentRows.length,
      open: tournamentRows.filter((tournament) => tournament.status === 'OPEN').length,
      active: tournamentRows.filter((tournament) => tournament.status === 'IN_PROGRESS').length,
      finished: tournamentRows.filter((tournament) => tournament.status === 'FINISHED').length,
    }),
    [tournamentRows],
  );

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <section className="page-header">
        <div>
          <div className="eyebrow">Gestión de torneos</div>
          <h1 className="page-title">Sigue el estado de tus eventos y administra rondas.</h1>
          <p className="page-copy">
            Esta vista junta el catálogo del organizador con standings y emparejamientos. Si el backend del torneo ya
            expone la ronda, puedes registrar resultados desde aquí.
          </p>
        </div>
        <div className="metric-inline-row">
          <div className="metric-inline-card">
            <div className="metric-label">Total</div>
            <div className="metric-inline-value">{kpis.total}</div>
          </div>
          <div className="metric-inline-card">
            <div className="metric-label">Open</div>
            <div className="metric-inline-value">{kpis.open}</div>
          </div>
          <div className="metric-inline-card">
            <div className="metric-label">En curso</div>
            <div className="metric-inline-value">{kpis.active}</div>
          </div>
          <div className="metric-inline-card">
            <div className="metric-label">Finalizados</div>
            <div className="metric-inline-value">{kpis.finished}</div>
          </div>
        </div>
      </section>

      <div className="panel-grid" style={{ gridTemplateColumns: '0.95fr 1.05fr' }}>
        <Card
          header={
            <div className="card-header-row">
              <span>Mis torneos</span>
              <Badge variant="info">{tournamentRows.length} registrados</Badge>
            </div>
          }
        >
          {tournaments.isLoading ? (
            <div style={{ display: 'grid', gap: 10 }}>
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} height={110} />
              ))}
            </div>
          ) : tournaments.isError ? (
            <ErrorAlert title="No se pudo cargar el listado" onRetry={() => tournaments.refetch()} />
          ) : tournamentRows.length === 0 ? (
            <EmptyState title="Aún no hay torneos creados" description="Cuando el BFF filtre por organizador, este tablero mostrará únicamente tus eventos." icon="♜" />
          ) : (
            <div style={{ display: 'grid', gap: 10 }}>
              {tournamentRows.map((tournament) => (
                <button
                  key={tournament.id}
                  type="button"
                  className="surface-button"
                  data-active={selectedTournamentId === tournament.id}
                  onClick={() => setSelectedTournamentId(tournament.id)}
                >
                  <div style={{ display: 'grid', gap: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                      <div style={{ textAlign: 'left' }}>
                        <div style={{ fontWeight: 700 }}>{tournament.name}</div>
                        <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                          {tournament.format} · {formatDate(tournament.startDate)}
                        </div>
                      </div>
                      <Badge variant={tournamentStatusVariant(tournament.status)}>{tournament.status}</Badge>
                    </div>
                    <div className="tournament-track">
                      <div className="track-segment" data-active={tournament.status === 'DRAFT'} />
                      <div className="track-segment" data-active={tournament.status === 'OPEN'} />
                      <div className="track-segment" data-active={tournament.status === 'IN_PROGRESS'} />
                      <div className="track-segment" data-active={tournament.status === 'FINISHED'} />
                    </div>
                    <div style={{ color: 'var(--text-muted)', fontSize: 12 }}>
                      {tournament.registered}/{tournament.maxPlayers} jugadores · {tournament.rounds} rondas
                    </div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </Card>

        <div style={{ display: 'grid', gap: 16 }}>
          <Card
            header={
              <div className="card-header-row">
                <span>{selectedTournament?.name ?? 'Selecciona un torneo'}</span>
                {selectedTournament ? (
                  <Badge variant={tournamentStatusVariant(selectedTournament.status)}>{selectedTournament.status}</Badge>
                ) : null}
              </div>
            }
          >
            {!selectedTournament ? (
              <EmptyState title="Sin torneo seleccionado" description="Elige un torneo del panel izquierdo para revisar su estado." icon="♔" />
            ) : (
              <div style={{ display: 'grid', gap: 16 }}>
                <div className="metric-grid">
                  <div className="metric-card">
                    <div className="metric-label">Formato</div>
                    <div className="metric-value" style={{ fontSize: 20 }}>{selectedTournament.format}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Jugadores</div>
                    <div className="metric-value">{selectedTournament.registered}/{selectedTournament.maxPlayers}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Rondas</div>
                    <div className="metric-value">{selectedTournament.rounds}</div>
                  </div>
                  <div className="metric-card">
                    <div className="metric-label">Sede</div>
                    <div className="metric-value" style={{ fontSize: 18 }}>{selectedTournament.location ?? 'Por definir'}</div>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'end' }}>
                  <Select
                    label="Ronda"
                    value={String(selectedRound)}
                    onChange={(event) => setSelectedRound(Number(event.target.value))}
                    options={Array.from({ length: Math.max(selectedTournament.rounds, 1) }, (_, index) => ({
                      value: String(index + 1),
                      label: `Ronda ${index + 1}`,
                    }))}
                    style={{ width: 180 }}
                  />
                  <Button
                    onClick={() => generateRound.mutate()}
                    loading={generateRound.isPending}
                    disabled={selectedTournament.status === 'DRAFT' || selectedTournament.status === 'FINISHED'}
                  >
                    Generar emparejamientos
                  </Button>
                </div>

                {generateRound.isError ? (
                  <ErrorAlert
                    title="No se pudo generar la ronda"
                    message={
                      (generateRound.error as { response?: { data?: { message?: string } }; message?: string })
                        ?.response?.data?.message ??
                      (generateRound.error as { message?: string })?.message ??
                      'Error desconocido al generar emparejamientos.'
                    }
                  />
                ) : null}
              </div>
            )}
          </Card>

          <Card header="Standings">
            {!selectedTournamentId ? (
              <EmptyState title="Sin clasificación" description="Selecciona un torneo para ver la tabla." icon="♟" />
            ) : standings.isLoading ? (
              <Skeleton height={220} />
            ) : standings.isError ? (
              <ErrorAlert title="No se pudo cargar la clasificación" onRetry={() => standings.refetch()} />
            ) : !standings.data || standings.data.length === 0 ? (
              <EmptyState title="Aún no hay standings" description="Los puntos aparecerán cuando existan rondas o resultados." icon="♞" />
            ) : (
              <StandingsTable entries={standings.data} />
            )}
          </Card>

          <Card header="Emparejamientos de ronda">
            {!selectedTournamentId ? (
              <EmptyState title="Sin ronda" description="Selecciona un torneo para revisar o registrar resultados." icon="♜" />
            ) : round.isLoading ? (
              <Skeleton height={240} />
            ) : round.isError ? (
              <EmptyState title={`La ronda ${selectedRound} aún no existe`} description="Usa el botón de generar emparejamientos cuando el torneo ya esté listo." icon="♘" />
            ) : !round.data || round.data.pairings.length === 0 ? (
              <EmptyState title="Sin pairings" description="Todavía no hay mesas generadas para esta ronda." icon="♙" />
            ) : (
              <Table<Pairing>
                rows={round.data.pairings}
                rowKey={(row) => row.id}
                columns={[
                  { key: 'board', header: 'Mesa', width: 64, render: (row) => row.boardNumber },
                  {
                    key: 'white',
                    header: 'Blancas',
                    render: (row) => `${row.whitePlayerName ?? `#${row.whitePlayerId ?? '—'}`} ${row.whitePlayerRating ? `(${row.whitePlayerRating})` : ''}`,
                  },
                  {
                    key: 'black',
                    header: 'Negras',
                    render: (row) => `${row.blackPlayerName ?? `#${row.blackPlayerId ?? '—'}`} ${row.blackPlayerRating ? `(${row.blackPlayerRating})` : ''}`,
                  },
                  {
                    key: 'result',
                    header: 'Resultado',
                    render: (row) => (
                      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        <Select
                          value={draftResults[row.id] ?? row.result ?? ''}
                          onChange={(event) => setDraftResults((current) => ({ ...current, [row.id]: event.target.value }))}
                          options={RESULT_OPTIONS}
                          style={{ width: 110 }}
                        />
                        <Button
                          size="sm"
                          onClick={() =>
                            patchResult.mutate({
                              pairingId: row.id,
                              result: draftResults[row.id] ?? row.result ?? '1-0',
                            })
                          }
                          loading={patchResult.isPending}
                        >
                          Guardar
                        </Button>
                      </div>
                    ),
                  },
                ] as TableColumn<Pairing>[]}
              />
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};
