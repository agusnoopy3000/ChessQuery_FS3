import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  Badge,
  Skeleton,
  ErrorAlert,
  EmptyState,
  StandingsTable,
  Table,
  TableColumn,
} from '@chessquery/ui-lib';
import { Tournament, Standing } from '@chessquery/shared';
import { tournamentApi, type MyRegistration } from '../api';

interface RegistrationCTAProps {
  tournament: Tournament;
  myRegistration: MyRegistration | null;
  loading: boolean;
  onRegister: () => void;
  registering: boolean;
  error: string | null;
}

const RegistrationCTA = ({ tournament, myRegistration, loading, onRegister, registering, error }: RegistrationCTAProps) => {
  if (loading) return null;

  // Si ya hay inscripción → mostrar estado.
  if (myRegistration) {
    const status = myRegistration.status;
    const meta: Record<MyRegistration['status'], { label: string; color: string; bg: string; description: string }> = {
      PENDING: {
        label: 'Inscripción pendiente',
        color: '#f0b94e', bg: 'rgba(240,185,78,0.1)',
        description: 'El organizador debe aprobar tu inscripción. Te avisaremos por notificación.',
      },
      CONFIRMED: {
        label: 'Inscripción confirmada',
        color: '#6abf74', bg: 'rgba(106,191,116,0.1)',
        description: 'Estás dentro del torneo. Te llegará la convocatoria al iniciar la primera ronda.',
      },
      REJECTED: {
        label: 'Inscripción rechazada',
        color: '#e05a5a', bg: 'rgba(224,90,90,0.1)',
        description: 'El organizador rechazó tu inscripción.',
      },
      CANCELLED: {
        label: 'Inscripción cancelada',
        color: '#7a7d6e', bg: 'rgba(122,125,110,0.1)',
        description: 'Cancelaste tu inscripción.',
      },
    };
    const m = meta[status];
    return (
      <div style={{
        marginTop: 16, padding: '12px 14px',
        background: m.bg, border: `1px solid ${m.color}`, borderRadius: 8,
        display: 'flex', flexDirection: 'column', gap: 4,
      }}>
        <strong style={{ color: m.color, fontSize: 14 }}>{m.label}</strong>
        <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>{m.description}</span>
      </div>
    );
  }

  // No hay inscripción → habilitar botón si el torneo está OPEN.
  const canRegister = tournament.status === 'OPEN';
  const reasonDisabled =
    tournament.status === 'DRAFT' ? 'Las inscripciones aún no abren para este torneo.'
    : tournament.status === 'IN_PROGRESS' ? 'El torneo ya empezó.'
    : tournament.status === 'FINISHED' ? 'El torneo finalizó.'
    : null;

  return (
    <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
      <Button onClick={onRegister} disabled={!canRegister || registering} loading={registering} variant="primary">
        Inscribirme en este torneo
      </Button>
      {!canRegister && reasonDisabled && (
        <span style={{ color: 'var(--text-muted)', fontSize: 12 }}>{reasonDisabled}</span>
      )}
      {error && (
        <div style={{
          background: 'rgba(224,90,90,0.1)', border: '1px solid #e05a5a',
          borderRadius: 8, padding: '8px 12px', color: '#e05a5a', fontSize: 13,
        }}>
          {error}
        </div>
      )}
    </div>
  );
};

type Tab = 'info' | 'standings' | 'rounds';

const statusVariant = (s: Tournament['status']) =>
  s === 'IN_PROGRESS' ? 'success' : s === 'OPEN' ? 'info' : s === 'FINISHED' ? 'neutral' : 'warning';

interface StandingsResp {
  standings?: Standing[];
  entries?: Standing[];
}

export const TournamentDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('info');

  const queryClient = useQueryClient();

  const detail = useQuery({
    queryKey: ['tournament', id],
    queryFn: () => tournamentApi.detail(id!),
    enabled: !!id,
  });

  const myReg = useQuery({
    queryKey: ['tournament', id, 'my-registration'],
    queryFn: () => tournamentApi.myRegistration(id!),
    enabled: !!id,
  });

  const register = useMutation({
    mutationFn: () => tournamentApi.register(id!),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['tournament', id, 'my-registration'] }),
        queryClient.invalidateQueries({ queryKey: ['tournament', id] }),
        queryClient.invalidateQueries({ queryKey: ['tournaments'] }),
      ]);
    },
  });

  const standings = useQuery({
    queryKey: ['tournament', id, 'standings'],
    queryFn: () => tournamentApi.standings(id!),
    enabled: !!id && tab === 'standings',
  });

  if (detail.isLoading) {
    return (
      <div style={{ padding: 28, display: 'grid', gap: 12 }}>
        <Skeleton height={120} />
        <Skeleton height={320} />
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

  const t = detail.data;
  const standingsData = standings.data as StandingsResp | Standing[] | undefined;
  const standingsList: Standing[] = Array.isArray(standingsData)
    ? standingsData
    : standingsData?.standings ?? standingsData?.entries ?? [];

  return (
    <div style={{ padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}>
      <button className="btn btn-ghost" style={{ alignSelf: 'flex-start' }} onClick={() => navigate('/tournaments')}>
        ← Volver a torneos
      </button>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 14, flexWrap: 'wrap' }}>
          <div>
            <h1 style={{ fontSize: 24, fontWeight: 700 }}>{t.name}</h1>
            <div style={{ display: 'flex', gap: 6, marginTop: 6, flexWrap: 'wrap' }}>
              <Badge variant={statusVariant(t.status)}>{t.status}</Badge>
              <Badge variant="info">{t.format}</Badge>
              <Badge>{t.rounds} rondas</Badge>
            </div>
          </div>
          <div style={{ textAlign: 'right', fontSize: 13, color: 'var(--text-muted)' }}>
            <div>📅 {t.startDate} {t.endDate ? `→ ${t.endDate}` : ''}</div>
            {t.location && <div>📍 {t.location}</div>}
            <div>👥 {t.registered ?? 0} / {t.maxPlayers}</div>
          </div>
        </div>

        <RegistrationCTA
          tournament={t}
          myRegistration={myReg.data ?? null}
          loading={myReg.isLoading}
          onRegister={() => register.mutate()}
          registering={register.isPending}
          error={
            (register.error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ??
            (register.error as { message?: string })?.message ??
            null
          }
        />
      </Card>

      <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--border)' }}>
        {(['info', 'standings', 'rounds'] as Tab[]).map((x) => (
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
            }}
          >
            {x === 'info' ? 'Información' : x === 'standings' ? 'Clasificación' : 'Rondas'}
          </button>
        ))}
      </div>

      {tab === 'info' && (
        <Card header="Información del torneo">
          <Table
            rows={[
              { k: 'Formato', v: t.format },
              { k: 'Estado', v: t.status },
              { k: 'Rondas', v: String(t.rounds) },
              { k: 'Inicio', v: t.startDate },
              { k: 'Fin', v: t.endDate ?? '—' },
              { k: 'Ubicación', v: t.location ?? '—' },
              { k: 'Cupos', v: `${t.registered ?? 0} / ${t.maxPlayers}` },
              { k: 'ELO mínimo', v: t.minElo != null ? String(t.minElo) : '—' },
              { k: 'ELO máximo', v: t.maxElo != null ? String(t.maxElo) : '—' },
              { k: 'Control de tiempo', v: t.timeControl ?? '—' },
            ]}
            rowKey={(r) => r.k}
            columns={[
              { key: 'k', header: 'Campo', width: 200, render: (r) => <strong>{r.k}</strong> },
              { key: 'v', header: 'Valor', render: (r) => r.v },
            ] as TableColumn<{ k: string; v: string }>[]}
          />
        </Card>
      )}

      {tab === 'standings' && (
        <Card header="Clasificación">
          {standings.isLoading ? (
            <Skeleton height={260} />
          ) : standings.isError ? (
            <ErrorAlert message="No se pudo cargar la clasificación" onRetry={() => standings.refetch()} />
          ) : standingsList.length === 0 ? (
            <EmptyState title="Aún sin clasificación" description="El torneo aún no genera standings" />
          ) : (
            <StandingsTable entries={standingsList} />
          )}
        </Card>
      )}

      {tab === 'rounds' && (
        <Card header="Rondas">
          <EmptyState
            title="Pendiente"
            description="La vista de pareos por ronda estará disponible próximamente"
            icon="♞"
          />
        </Card>
      )}
    </div>
  );
};
