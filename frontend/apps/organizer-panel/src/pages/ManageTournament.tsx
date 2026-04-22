import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Card,
  Badge,
  Button,
  Skeleton,
  ErrorAlert,
  EmptyState,
  Table,
  TableColumn,
  StandingsTable,
  Select,
} from '@chessquery/ui-lib';
import { Tournament, TournamentRegistration, Standing, Pairing, Round } from '@chessquery/shared';
import { organizerApi } from '../api';

type Tab = 'inscripciones' | 'rondas' | 'standings';

interface StandingsResp {
  standings?: Standing[];
  entries?: Standing[];
}

export const ManageTournamentPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>('inscripciones');
  const [activeRound, setActiveRound] = useState(1);

  const detail = useQuery({
    queryKey: ['organizer', 'tournament', id],
    queryFn: () => organizerApi.getTournament(id!),
    enabled: !!id,
  });

  const registrations = useQuery({
    queryKey: ['organizer', 'tournament', id, 'registrations'],
    queryFn: () => organizerApi.listRegistrations(id!),
    enabled: !!id && tab === 'inscripciones',
  });

  const round = useQuery({
    queryKey: ['organizer', 'tournament', id, 'round', activeRound],
    queryFn: () => organizerApi.getRound(id!, activeRound),
    enabled: !!id && tab === 'rondas',
    retry: false,
  });

  const standings = useQuery({
    queryKey: ['organizer', 'tournament', id, 'standings'],
    queryFn: () => organizerApi.getStandings(id!),
    enabled: !!id && tab === 'standings',
  });

  const generateRound = useMutation({
    mutationFn: () => organizerApi.generateRound(id!, activeRound),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['organizer', 'tournament', id, 'round', activeRound] }),
  });

  const setResult = useMutation({
    mutationFn: ({ pid, result }: { pid: number; result: string }) =>
      organizerApi.setPairingResult(pid, result),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['organizer', 'tournament', id] }),
  });

  if (detail.isLoading) {
    return (
      <div style={{ padding: 28 }}>
        <Skeleton height={120} />
      </div>
    );
  }

  if (detail.isError || !detail.data) {
    return (
      <div style={{ padding: 28 }}>
        <ErrorAlert title="Torneo no encontrado" onRetry={() => detail.refetch()} />
      </div>
    );
  }

  const t: Tournament = detail.data;
  const regs: TournamentRegistration[] = Array.isArray(registrations.data) ? registrations.data : [];
  const roundData = round.data as Round | undefined;
  const standingsRaw = standings.data as StandingsResp | Standing[] | undefined;
  const standingsList: Standing[] = Array.isArray(standingsRaw)
    ? standingsRaw
    : standingsRaw?.standings ?? standingsRaw?.entries ?? [];

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <button className="btn btn-ghost" style={{ alignSelf: 'flex-start' }} onClick={() => navigate('/')}>
        ← Volver
      </button>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: 22, fontWeight: 700 }}>{t.name}</h1>
            <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
              <Badge>{t.format}</Badge>
              <Badge variant="info">{t.status}</Badge>
              <Badge>{t.rounds} rondas</Badge>
            </div>
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-muted)', textAlign: 'right' }}>
            <div>📅 {t.startDate}</div>
            {t.location && <div>📍 {t.location}</div>}
            <div>👥 {t.registered ?? 0} / {t.maxPlayers}</div>
          </div>
        </div>
      </Card>

      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--border)' }}>
        {(['inscripciones', 'rondas', 'standings'] as Tab[]).map((x) => (
          <button
            key={x}
            type="button"
            onClick={() => setTab(x)}
            style={{
              padding: '10px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: tab === x ? '2px solid var(--accent)' : '2px solid transparent',
              color: tab === x ? 'var(--accent)' : 'var(--text-muted)',
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
              textTransform: 'capitalize',
            }}
          >
            {x}
          </button>
        ))}
      </div>

      {tab === 'inscripciones' && (
        <Card header="Inscripciones">
          {registrations.isLoading ? (
            <Skeleton height={200} />
          ) : regs.length === 0 ? (
            <EmptyState title="Sin inscripciones" />
          ) : (
            <Table<TournamentRegistration>
              rows={regs}
              rowKey={(r) => r.id}
              columns={[
                { key: 'name', header: 'Jugador', render: (r) => r.playerName ?? `#${r.playerId}` },
                { key: 'seed', header: 'Seed ELO', align: 'right', render: (r) => r.seedRating },
                { key: 'status', header: 'Estado', render: (r) => <Badge>{r.status}</Badge> },
                { key: 'date', header: 'Inscrito', render: (r) => r.registeredAt?.slice(0, 10) },
              ] as TableColumn<TournamentRegistration>[]}
            />
          )}
        </Card>
      )}

      {tab === 'rondas' && (
        <Card
          header={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
              <span>Ronda</span>
              <div style={{ display: 'flex', gap: 8 }}>
                <Select
                  value={String(activeRound)}
                  onChange={(e) => setActiveRound(Number(e.target.value))}
                  options={Array.from({ length: t.rounds }, (_, i) => ({
                    value: String(i + 1),
                    label: `Ronda ${i + 1}`,
                  }))}
                  style={{ width: 140 }}
                />
                <Button
                  size="sm"
                  onClick={() => generateRound.mutate()}
                  loading={generateRound.isPending}
                  disabled={!!roundData?.pairings?.length}
                >
                  Generar pareos
                </Button>
              </div>
            </div>
          }
        >
          {round.isLoading ? (
            <Skeleton height={200} />
          ) : !roundData || roundData.pairings?.length === 0 ? (
            <EmptyState
              title="Sin pareos"
              description={`La ronda ${activeRound} aún no tiene pareos generados`}
              icon="♞"
            />
          ) : (
            <Table<Pairing>
              rows={roundData.pairings}
              rowKey={(r) => r.id}
              columns={[
                { key: 'board', header: 'Mesa', width: 60, align: 'center', render: (r) => r.boardNumber },
                {
                  key: 'white',
                  header: 'Blancas',
                  render: (r) => (
                    <span>
                      {r.whitePlayerName ?? `#${r.whitePlayerId}`}
                      {r.whitePlayerRating && (
                        <span style={{ color: 'var(--text-muted)', marginLeft: 6 }}>({r.whitePlayerRating})</span>
                      )}
                    </span>
                  ),
                },
                {
                  key: 'black',
                  header: 'Negras',
                  render: (r) => (
                    <span>
                      {r.blackPlayerName ?? (r.blackPlayerId ? `#${r.blackPlayerId}` : 'BYE')}
                      {r.blackPlayerRating && (
                        <span style={{ color: 'var(--text-muted)', marginLeft: 6 }}>({r.blackPlayerRating})</span>
                      )}
                    </span>
                  ),
                },
                {
                  key: 'result',
                  header: 'Resultado',
                  align: 'center',
                  render: (r) => (
                    <Select
                      value={r.result ?? ''}
                      onChange={(e) => setResult.mutate({ pid: r.id, result: e.target.value })}
                      options={[
                        { value: '', label: '—' },
                        { value: '1-0', label: '1-0' },
                        { value: '0-1', label: '0-1' },
                        { value: '1/2-1/2', label: '½-½' },
                      ]}
                      style={{ width: 100 }}
                    />
                  ),
                },
              ] as TableColumn<Pairing>[]}
            />
          )}
        </Card>
      )}

      {tab === 'standings' && (
        <Card header="Clasificación">
          {standings.isLoading ? (
            <Skeleton height={260} />
          ) : standingsList.length === 0 ? (
            <EmptyState title="Aún sin clasificación" />
          ) : (
            <StandingsTable entries={standingsList} />
          )}
        </Card>
      )}
    </div>
  );
};
