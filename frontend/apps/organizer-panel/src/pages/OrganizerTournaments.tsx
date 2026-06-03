import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Badge, Button, Card, EmptyState, ErrorAlert, Select, Skeleton, StandingsTable, Table, TableColumn } from '@chessquery/ui-lib';
import { Pairing, Tournament } from '@chessquery/shared';
import { organizerApi, type CreateTournamentInput, type RegistrationRow } from '../api';
import { dedupeBy, formatDate, tournamentStatusVariant, unwrapContent } from '../portal-utils';
import { CreateTournamentModal } from '../components/CreateTournamentModal';
import { RegistrationsPanel } from '../components/RegistrationsPanel';
import { LiveSpectatorModal } from '../components/LiveSpectatorModal';

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
  const [statusFilter, setStatusFilter] = useState<'ALL' | Tournament['status']>('ALL');
  const [search, setSearch] = useState('');
  const [spectating, setSpectating] = useState<{ sessionId: number; white: string; black: string } | null>(null);

  const tournaments = useQuery({
    queryKey: ['organizer', 'tournaments', 'list'],
    queryFn: () => organizerApi.listTournaments({ size: 20 }),
  });

  const tournamentRows = useMemo(
    () => dedupeBy(unwrapContent<Tournament>(tournaments.data), (t) => t.id),
    [tournaments.data],
  );

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

  // Mientras el torneo está en progreso, refrescamos en vivo para que la grilla
  // y la clasificación reflejen los resultados que entran solos al terminar las
  // partidas (game.finished → recordResult en el backend).
  const liveRefetch = selectedTournament?.status === 'IN_PROGRESS' ? 8000 : false;

  const standings = useQuery({
    queryKey: ['organizer', 'tournaments', 'standings', selectedTournamentId],
    queryFn: () => organizerApi.tournamentStandings(selectedTournamentId!),
    enabled: selectedTournamentId != null,
    refetchInterval: liveRefetch,
  });

  const round = useQuery({
    queryKey: ['organizer', 'tournaments', 'round', selectedTournamentId, selectedRound],
    queryFn: () => organizerApi.round(selectedTournamentId!, selectedRound),
    enabled: selectedTournamentId != null,
    refetchInterval: liveRefetch,
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

  const [showCreateModal, setShowCreateModal] = useState(false);

  const createTournament = useMutation({
    mutationFn: (input: CreateTournamentInput) => organizerApi.createTournament(input),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] });
      setSelectedTournamentId(created.id);
      setShowCreateModal(false);
    },
  });

  const patchStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: Tournament['status'] }) =>
      organizerApi.patchTournamentStatus(id, status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] });
    },
  });

  const registrations = useQuery({
    queryKey: ['organizer', 'tournaments', 'registrations', selectedTournamentId],
    queryFn: () => organizerApi.listRegistrations(selectedTournamentId!),
    enabled: selectedTournamentId != null,
  });

  const approveReg = useMutation({
    mutationFn: (registrationId: number) => organizerApi.approveRegistration(registrationId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'registrations', selectedTournamentId] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] }),
      ]);
    },
  });

  const rejectReg = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason?: string }) =>
      organizerApi.rejectRegistration(id, reason),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'registrations', selectedTournamentId] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] }),
      ]);
    },
  });

  const deleteTournament = useMutation({
    mutationFn: (id: number) => organizerApi.deleteTournament(id),
    onSuccess: async (_data, deletedId) => {
      // Si se borró el torneo seleccionado, limpiamos la selección.
      if (selectedTournamentId === deletedId) {
        setSelectedTournamentId(null);
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] }),
        queryClient.invalidateQueries({ queryKey: ['organizer', 'portal', 'tournaments'] }),
      ]);
    },
  });

  const handleDelete = (t: Tournament) => {
    const ok = window.confirm(
      `¿Eliminar el torneo "${t.name}"?\n\nEsta acción es irreversible. Solo se permite eliminar torneos en DRAFT u OPEN sin rondas generadas.`,
    );
    if (!ok) return;
    deleteTournament.mutate(t.id);
  };

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'list'] });
    if (selectedTournamentId != null) {
      queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'registrations', selectedTournamentId] });
      queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'standings', selectedTournamentId] });
      queryClient.invalidateQueries({ queryKey: ['organizer', 'tournaments', 'round', selectedTournamentId, selectedRound] });
    }
  };

  const registrationRows: RegistrationRow[] = useMemo(
    () => (Array.isArray(registrations.data) ? registrations.data : []),
    [registrations.data],
  );

  const kpis = useMemo(
    () => ({
      total: tournamentRows.length,
      open: tournamentRows.filter((tournament) => tournament.status === 'OPEN').length,
      active: tournamentRows.filter((tournament) => tournament.status === 'IN_PROGRESS').length,
      finished: tournamentRows.filter((tournament) => tournament.status === 'FINISHED').length,
    }),
    [tournamentRows],
  );

  const visibleTournaments = useMemo(() => {
    const term = search.trim().toLowerCase();
    return tournamentRows.filter((tournament) => {
      const matchesStatus = statusFilter === 'ALL' || tournament.status === statusFilter;
      const matchesSearch =
        !term ||
        tournament.name.toLowerCase().includes(term) ||
        tournament.format.toLowerCase().includes(term) ||
        (tournament.location ?? '').toLowerCase().includes(term);
      return matchesStatus && matchesSearch;
    });
  }, [search, statusFilter, tournamentRows]);

  const filterTabs: Array<{ key: 'ALL' | Tournament['status']; label: string; count: number }> = [
    { key: 'ALL', label: 'Todos', count: tournamentRows.length },
    { key: 'DRAFT', label: 'Borrador', count: tournamentRows.filter((t) => t.status === 'DRAFT').length },
    { key: 'OPEN', label: 'Inscripciones', count: kpis.open },
    { key: 'IN_PROGRESS', label: 'En curso', count: kpis.active },
    { key: 'FINISHED', label: 'Finalizados', count: kpis.finished },
  ];

  return (
    <div style={{ padding: 28, display: 'grid', gap: 20 }}>
      <style>{`
        @keyframes cq-spin { to { transform: rotate(360deg); } }
        .cq-filter-row {
          display: flex;
          gap: 8px;
          flex-wrap: wrap;
          margin-bottom: 14px;
        }
        .cq-filter-chip {
          border: 1px solid var(--border, #2a2d27);
          border-radius: 999px;
          background: rgba(255,255,255,0.03);
          color: var(--text-muted, #7a7d6e);
          padding: 7px 11px;
          font: inherit;
          font-size: 12px;
          cursor: pointer;
        }
        .cq-filter-chip[data-active='true'] {
          background: rgba(106,191,116,0.12);
          border-color: rgba(106,191,116,0.42);
          color: #6abf74;
        }
        .cq-list-tools {
          display: grid;
          grid-template-columns: minmax(0, 1fr) auto;
          gap: 10px;
          margin-bottom: 12px;
        }
        .cq-search-input {
          width: 100%;
          height: 36px;
          border: 1px solid var(--border, #2a2d27);
          border-radius: 10px;
          background: rgba(14,16,13,0.72);
          color: var(--text, #e8ead4);
          padding: 0 12px;
          outline: none;
        }
        .cq-search-input:focus {
          border-color: rgba(106,191,116,0.45);
          box-shadow: 0 0 0 3px rgba(106,191,116,0.12);
        }
        @media (max-width: 720px) {
          .cq-list-tools { grid-template-columns: 1fr; }
        }
      `}</style>
      {deleteTournament.isError && (
        <ErrorAlert
          title="No se pudo eliminar el torneo"
          message={
            (deleteTournament.error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ??
            (deleteTournament.error as { message?: string })?.message ??
            'Error desconocido al eliminar.'
          }
        />
      )}
      <section className="page-header">
        <div>
          <div className="eyebrow">Gestión de torneos</div>
          <h1 className="page-title">Crea, gestiona e impulsa tus torneos.</h1>
          <p className="page-copy">
            Tus torneos en un solo lugar: revisa el estado de cada uno, aprueba inscripciones,
            genera emparejamientos ronda por ronda y registra los resultados a medida que se juegan.
          </p>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, alignItems: 'flex-end' }}>
          <Button
            onClick={() => setShowCreateModal(true)}
            variant="primary"
            size="lg"
            style={{ minWidth: 200, gap: 8, fontWeight: 700, letterSpacing: '0.01em' }}
          >
            <span style={{ fontSize: 18, lineHeight: 1 }}>＋</span> Crear torneo
          </Button>
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
        </div>
      </section>

      <CreateTournamentModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onSubmit={(input) => createTournament.mutate(input)}
        loading={createTournament.isPending}
        error={
          (createTournament.error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ??
          (createTournament.error as { message?: string })?.message ??
          null
        }
      />

      <div className="panel-grid" style={{ gridTemplateColumns: 'minmax(340px, 0.85fr) minmax(420px, 1.15fr)', gap: 18 }}>
        <Card
          header={
            <div className="card-header-row">
              <span style={{ fontSize: 15, fontWeight: 700 }}>Mis torneos</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Badge variant="info">{tournamentRows.length} registrados</Badge>
                <button
                  type="button"
                  onClick={handleRefresh}
                  title="Actualizar lista"
                  aria-label="Actualizar lista"
                  disabled={tournaments.isFetching}
                  style={{
                    background: 'rgba(255,255,255,0.04)',
                    border: '1px solid var(--border, #2a2d27)',
                    borderRadius: 8,
                    width: 30, height: 30,
                    color: 'var(--text-muted, #7a7d6e)',
                    cursor: tournaments.isFetching ? 'wait' : 'pointer',
                    fontSize: 14,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    transition: 'border-color 0.15s, color 0.15s',
                  }}
                  onMouseEnter={(e) => {
                    if (!tournaments.isFetching) {
                      e.currentTarget.style.borderColor = 'rgba(106,191,116,0.4)';
                      e.currentTarget.style.color = '#6abf74';
                    }
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = 'var(--border, #2a2d27)';
                    e.currentTarget.style.color = 'var(--text-muted, #7a7d6e)';
                  }}
                >
                  <span
                    style={{
                      display: 'inline-block',
                      animation: tournaments.isFetching ? 'cq-spin 0.8s linear infinite' : 'none',
                    }}
                  >
                    ↻
                  </span>
                </button>
              </div>
            </div>
          }
        >
          {tournaments.isLoading ? (
            <div style={{ display: 'grid', gap: 12 }}>
              {Array.from({ length: 4 }).map((_, index) => (
                <Skeleton key={index} height={120} />
              ))}
            </div>
          ) : tournaments.isError ? (
            <ErrorAlert title="No se pudo cargar el listado" onRetry={() => tournaments.refetch()} />
          ) : tournamentRows.length === 0 ? (
            <EmptyState
              title="Aún no hay torneos creados"
              description="Crea tu primer torneo y los jugadores podrán inscribirse desde su cuenta."
              icon="♜"
              action={
                <Button
                  onClick={() => setShowCreateModal(true)}
                  variant="primary"
                  size="lg"
                  style={{ gap: 8 }}
                >
                  <span style={{ fontSize: 18, lineHeight: 1 }}>＋</span> Crear torneo
                </Button>
              }
            />
          ) : (
            <>
              <div className="cq-list-tools">
                <input
                  className="cq-search-input"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Buscar por nombre, formato o sede"
                  aria-label="Buscar torneos"
                />
                <Badge variant="neutral">{visibleTournaments.length} visibles</Badge>
              </div>
              <div className="cq-filter-row" aria-label="Filtrar torneos por estado">
                {filterTabs.map((tab) => (
                  <button
                    key={tab.key}
                    type="button"
                    className="cq-filter-chip"
                    data-active={statusFilter === tab.key}
                    onClick={() => setStatusFilter(tab.key)}
                  >
                    {tab.label} ({tab.count})
                  </button>
                ))}
              </div>
              {visibleTournaments.length === 0 ? (
                <EmptyState
                  title="Sin torneos para este filtro"
                  description="Ajusta el estado o la búsqueda para volver a ver torneos."
                  icon="♜"
                />
              ) : (
                <div style={{ display: 'grid', gap: 12 }}>
                  {visibleTournaments.map((tournament) => {
                const canDelete =
                  tournament.status === 'DRAFT' || tournament.status === 'OPEN';
                const isDeleting =
                  deleteTournament.isPending && deleteTournament.variables === tournament.id;
                return (
                  <div
                    key={tournament.id}
                    style={{ position: 'relative' }}
                  >
                    <button
                      type="button"
                      className="surface-button"
                      data-active={selectedTournamentId === tournament.id}
                      onClick={() => setSelectedTournamentId(tournament.id)}
                      style={{ paddingRight: canDelete ? 52 : 18, paddingLeft: 18, paddingTop: 14, paddingBottom: 14 }}
                    >
                      <div style={{ display: 'grid', gap: 10 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' }}>
                          <div style={{ textAlign: 'left', minWidth: 0, flex: 1 }}>
                            <div style={{ fontWeight: 700, fontSize: 14, lineHeight: 1.3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{tournament.name}</div>
                            <div style={{ color: 'var(--text-muted)', fontSize: 11, marginTop: 3, letterSpacing: '0.02em' }}>
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
                        <div style={{ display: 'flex', gap: 14, fontSize: 11, color: 'var(--text-muted)' }}>
                          <span><strong style={{ color: 'var(--text, #e8ead4)' }}>{tournament.registered}/{tournament.maxPlayers}</strong> jugadores</span>
                          <span><strong style={{ color: 'var(--text, #e8ead4)' }}>{tournament.rounds}</strong> rondas</span>
                        </div>
                      </div>
                    </button>
                    {canDelete && (
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleDelete(tournament);
                        }}
                        disabled={isDeleting}
                        title={`Eliminar "${tournament.name}"`}
                        aria-label="Eliminar torneo"
                        style={{
                          position: 'absolute',
                          top: 12,
                          right: 12,
                          width: 28,
                          height: 28,
                          background: 'rgba(224,90,90,0.08)',
                          border: '1px solid rgba(224,90,90,0.3)',
                          borderRadius: 6,
                          color: '#e05a5a',
                          cursor: isDeleting ? 'wait' : 'pointer',
                          fontSize: 13,
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          opacity: isDeleting ? 0.6 : 1,
                          transition: 'background 0.15s, transform 0.1s',
                        }}
                        onMouseEnter={(e) => {
                          if (!isDeleting) {
                            e.currentTarget.style.background = 'rgba(224,90,90,0.18)';
                            e.currentTarget.style.transform = 'scale(1.05)';
                          }
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = 'rgba(224,90,90,0.08)';
                          e.currentTarget.style.transform = 'scale(1)';
                        }}
                      >
                        🗑
                      </button>
                    )}
                  </div>
                );
                  })}
                </div>
              )}
            </>
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
              <EmptyState title="Sin torneo seleccionado" description="Elige un torneo de la lista para ver su detalle y administrarlo." icon="♔" />
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

                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
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
                    size="lg"
                    variant="primary"
                    style={{ minWidth: 220, gap: 8, fontWeight: 700 }}
                  >
                    <span style={{ fontSize: 16, lineHeight: 1 }}>♞</span> Generar emparejamientos
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

                {/* Acciones de transición de estado del torneo */}
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginTop: 4 }}>
                  {selectedTournament.status === 'DRAFT' && (
                    <Button
                      variant="primary"
                      size="lg"
                      onClick={() => patchStatus.mutate({ id: selectedTournament.id, status: 'OPEN' })}
                      loading={patchStatus.isPending}
                      style={{ minWidth: 200, gap: 8, fontWeight: 700 }}
                    >
                      <span style={{ fontSize: 15, lineHeight: 1 }}>📝</span> Abrir inscripciones
                    </Button>
                  )}
                  {selectedTournament.status === 'OPEN' && (
                    <Button
                      variant="primary"
                      size="lg"
                      onClick={() => patchStatus.mutate({ id: selectedTournament.id, status: 'IN_PROGRESS' })}
                      loading={patchStatus.isPending}
                      style={{ minWidth: 200, gap: 8, fontWeight: 700 }}
                    >
                      <span style={{ fontSize: 15, lineHeight: 1 }}>▶</span> Iniciar torneo
                    </Button>
                  )}
                  {selectedTournament.status === 'IN_PROGRESS' && (
                    <Button
                      variant="secondary"
                      size="lg"
                      onClick={() => patchStatus.mutate({ id: selectedTournament.id, status: 'FINISHED' })}
                      loading={patchStatus.isPending}
                      style={{ minWidth: 200, gap: 8, fontWeight: 700 }}
                    >
                      <span style={{ fontSize: 15, lineHeight: 1 }}>🏁</span> Finalizar torneo
                    </Button>
                  )}
                </div>
                {patchStatus.isError ? (
                  <ErrorAlert
                    title="No se pudo cambiar el estado"
                    message={
                      (patchStatus.error as { response?: { data?: { message?: string } }; message?: string })
                        ?.response?.data?.message ??
                      'Error desconocido al transicionar estado.'
                    }
                  />
                ) : null}
              </div>
            )}
          </Card>

          {/* Inscripciones (PENDING + CONFIRMED + REJECTED) */}
          {selectedTournamentId != null && (
            <RegistrationsPanel
              registrations={registrationRows}
              loading={registrations.isLoading}
              error={registrations.isError ? 'No se pudieron cargar las inscripciones' : null}
              onApprove={(rid) => approveReg.mutate(rid)}
              onReject={(rid, reason) => rejectReg.mutate({ id: rid, reason })}
              busyId={
                approveReg.isPending
                  ? (approveReg.variables as number)
                  : rejectReg.isPending
                    ? ((rejectReg.variables as { id: number } | undefined)?.id ?? null)
                    : null
              }
            />
          )}

          <Card header="Standings">
            {!selectedTournamentId ? (
              <EmptyState title="Sin clasificación" description="Elige un torneo para ver su tabla de posiciones." icon="♟" />
            ) : standings.isLoading ? (
              <Skeleton height={220} />
            ) : standings.isError ? (
              <ErrorAlert title="No se pudo cargar la clasificación" onRetry={() => standings.refetch()} />
            ) : !standings.data || standings.data.length === 0 ? (
              <EmptyState title="Aún no hay clasificación" description="Los puntos aparecerán cuando se carguen los resultados de la primera ronda." icon="♞" />
            ) : (
              <StandingsTable entries={standings.data} />
            )}
          </Card>

          <Card
            header={
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span>Emparejamientos de ronda</span>
                {(() => {
                  const liveCount = (round.data?.pairings ?? []).filter((p) => p.liveSessionId && !p.result).length;
                  return liveCount > 0 ? (
                    <Badge variant="success">🔴 {liveCount} en vivo</Badge>
                  ) : null;
                })()}
              </div>
            }
          >
            {!selectedTournamentId ? (
              <EmptyState title="Sin ronda" description="Elige un torneo para ver y administrar sus rondas." icon="♜" />
            ) : round.isLoading ? (
              <Skeleton height={240} />
            ) : round.isError ? (
              <EmptyState title={`La ronda ${selectedRound} aún no existe`} description="Cuando el torneo esté listo para comenzar, genera los emparejamientos con el botón de arriba." icon="♘" />
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
                    key: 'live',
                    header: 'Partida',
                    width: 130,
                    render: (row) =>
                      row.liveSessionId ? (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() =>
                            setSpectating({
                              sessionId: row.liveSessionId!,
                              white: row.whitePlayerName ?? `#${row.whitePlayerId ?? '—'}`,
                              black: row.blackPlayerName ?? `#${row.blackPlayerId ?? '—'}`,
                            })
                          }
                        >
                          👁 Ver en vivo
                        </Button>
                      ) : (
                        <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>—</span>
                      ),
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

      {spectating && (
        <LiveSpectatorModal
          sessionId={spectating.sessionId}
          whiteLabel={spectating.white}
          blackLabel={spectating.black}
          onClose={() => setSpectating(null)}
        />
      )}
    </div>
  );
};
