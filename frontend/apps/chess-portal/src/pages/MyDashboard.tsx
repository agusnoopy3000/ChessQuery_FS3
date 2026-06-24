import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  RatingBadge,
  Skeleton,
  ErrorAlert,
  EmptyState,
} from '@chessquery/ui-lib';
import { playerApi, type LichessProfilePayload } from '../api';

interface PlatformRating {
  variant: string;
  rating: number | null;
  games: number | null;
  prog: number | null;
}

const VARIANT_LABEL: Record<string, string> = {
  bullet: 'Bullet',
  blitz: 'Blitz',
  rapid: 'Rapid',
  classical: 'Classical',
  daily: 'Daily',
  correspondence: 'Correspondence',
};

// Variantes que mostramos por plataforma (en orden).
const LICHESS_VARIANTS = ['bullet', 'blitz', 'rapid', 'classical'];
const CHESSCOM_VARIANTS = ['bullet', 'blitz', 'rapid', 'daily'];

const PAGE_PAD = 'clamp(14px, 4vw, 28px)';

export const MyDashboardPage = () => {
  const qc = useQueryClient();

  const dashboard = useQuery({
    queryKey: ['me', 'dashboard'],
    queryFn: () => playerApi.dashboard(),
  });

  const playerId = dashboard.data?.profile.id;
  const lichessUser = dashboard.data?.profile.lichessUsername;
  const chesscomUser = dashboard.data?.profile.chesscomUsername;

  const lichess = useQuery({
    queryKey: ['me', 'lichess'],
    queryFn: () => playerApi.lichess(String(playerId)),
    enabled: !!playerId && !!lichessUser,
    retry: false,
  });

  const chesscom = useQuery({
    queryKey: ['me', 'chesscom'],
    queryFn: () => playerApi.chesscom(String(playerId)),
    enabled: !!playerId && !!chesscomUser,
    retry: false,
  });

  if (dashboard.isLoading) {
    return (
      <div style={{ padding: PAGE_PAD, display: 'grid', gap: 12 }}>
        <Skeleton height={100} />
        <Skeleton height={220} />
      </div>
    );
  }

  if (dashboard.isError || !dashboard.data) {
    return (
      <div style={{ padding: PAGE_PAD }}>
        <ErrorAlert title="No se pudo cargar tu dashboard" onRetry={() => dashboard.refetch()} />
      </div>
    );
  }

  const { profile: p } = dashboard.data;
  const fullName = [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Jugador';

  return (
    <div
      style={{
        padding: PAGE_PAD,
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        maxWidth: 980,
        margin: '0 auto',
      }}
    >
      <div>
        <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Bienvenido de vuelta</div>
        <h1 style={{ fontSize: 26, fontWeight: 700 }}>{fullName}</h1>
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: 12,
        }}
      >
        <Card header="Nombre completo">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{fullName}</div>
        </Card>
        <Card header="ELO Nacional">
          {p.eloNational != null ? (
            <RatingBadge rating={p.eloNational} label="NAC" />
          ) : (
            <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>
          )}
        </Card>
        <Card header="ELO Internacional">
          {p.eloFideStandard != null ? (
            <RatingBadge rating={p.eloFideStandard} label="FIDE" />
          ) : (
            <div style={{ fontSize: 18, color: 'var(--text-muted)' }}>—</div>
          )}
        </Card>
        <Card header="ELO ChessQuery">
          {p.eloPlatform != null ? (
            <div>
              <RatingBadge rating={p.eloPlatform} label="CQ" />
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 6 }}>
                Actualizado por tus partidas
              </div>
            </div>
          ) : (
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>Aún sin partidas jugadas</div>
          )}
        </Card>
        <Card header="Club">
          <div style={{ fontSize: 18, fontWeight: 600 }}>{p.clubName ?? '—'}</div>
        </Card>
      </div>

      <LinkPlatformsCard
        lichessUsername={lichessUser ?? ''}
        chesscomUsername={chesscomUser ?? ''}
        onSaved={() => {
          qc.invalidateQueries({ queryKey: ['me', 'dashboard'] });
          qc.invalidateQueries({ queryKey: ['me', 'lichess'] });
          qc.invalidateQueries({ queryKey: ['me', 'chesscom'] });
        }}
      />

      <PlatformCard
        title="Lichess"
        icon="♞"
        username={lichessUser ?? null}
        variants={LICHESS_VARIANTS}
        query={lichess}
      />

      <PlatformCard
        title="Chess.com"
        icon="♟"
        username={chesscomUser ?? null}
        variants={CHESSCOM_VARIANTS}
        query={chesscom}
      />
    </div>
  );
};

// ── Tabla de ratings de una plataforma (reusada por Lichess y Chess.com) ────────
interface PlatformCardProps {
  title: string;
  icon: string;
  username: string | null;
  variants: string[];
  query: {
    isLoading: boolean;
    isError: boolean;
    data?: LichessProfilePayload;
    refetch: () => void;
  };
}

const PlatformCard = ({ title, icon, username, variants, query }: PlatformCardProps) => {
  const ratings = ((query.data?.ratings ?? []) as PlatformRating[]).filter((r) =>
    variants.includes(r.variant),
  );

  return (
    <Card header={`Resumen ${title}${username ? ` · @${username}` : ''}`}>
      {!username ? (
        <EmptyState
          title={`Sin cuenta ${title} vinculada`}
          description={`Vincula tu usuario de ${title} más arriba para ver tu rating de cada modalidad.`}
          icon={icon}
        />
      ) : query.isLoading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 10 }}>
          {Array.from({ length: variants.length }).map((_, i) => (
            <Skeleton key={i} height={86} />
          ))}
        </div>
      ) : query.isError ? (
        <ErrorAlert title={`No se pudo consultar ${title}`} onRetry={() => query.refetch()} />
      ) : !query.data?.found ? (
        <EmptyState
          title={`Usuario @${username} no encontrado en ${title}`}
          description={query.data?.error ?? `No encontramos esa cuenta en ${title}. Revisa el usuario en tu perfil.`}
          icon="⚠"
        />
      ) : ratings.length === 0 ? (
        <EmptyState title="Sin ratings disponibles" description="No tienes partidas rateadas." />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 10 }}>
          {ratings.map((r) => (
            <div
              key={r.variant}
              style={{
                padding: '14px 16px',
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 12,
              }}
            >
              <div
                style={{
                  fontSize: 11,
                  color: 'var(--text-muted)',
                  textTransform: 'uppercase',
                  letterSpacing: 0.5,
                  fontWeight: 600,
                }}
              >
                {VARIANT_LABEL[r.variant] ?? r.variant}
              </div>
              <div style={{ fontSize: 26, fontWeight: 700, marginTop: 2 }}>{r.rating ?? '—'}</div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
                {r.games ?? 0} partidas
                {r.prog != null && r.prog !== 0 ? (
                  <span style={{ marginLeft: 6, color: r.prog > 0 ? 'var(--accent)' : 'var(--red)' }}>
                    {r.prog > 0 ? '+' : ''}
                    {r.prog}
                  </span>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
};

// ── Vincular / editar usuarios de plataforma + sincronizar ──────────────────────
interface LinkPlatformsCardProps {
  lichessUsername: string;
  chesscomUsername: string;
  onSaved: () => void;
}

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '9px 11px',
  borderRadius: 8,
  background: 'var(--cq-input-bg, #0e100d)',
  border: '1px solid var(--cq-border, #2a2d27)',
  color: 'var(--cq-text, #e8ead4)',
  fontSize: 13,
  outline: 'none',
};

const LinkPlatformsCard = ({ lichessUsername, chesscomUsername, onSaved }: LinkPlatformsCardProps) => {
  const [lichess, setLichess] = useState(lichessUsername);
  const [chesscom, setChesscom] = useState(chesscomUsername);

  // Sincronizar el estado local si el perfil cambia (p.ej. tras refetch).
  useEffect(() => setLichess(lichessUsername), [lichessUsername]);
  useEffect(() => setChesscom(chesscomUsername), [chesscomUsername]);

  const mutation = useMutation({
    mutationFn: () =>
      playerApi.updateProfile({
        lichessUsername: lichess.trim() || undefined,
        chesscomUsername: chesscom.trim() || undefined,
      }),
    onSuccess: () => onSaved(),
  });

  const dirty = lichess.trim() !== lichessUsername.trim() || chesscom.trim() !== chesscomUsername.trim();
  const errMsg =
    mutation.error instanceof Error ? mutation.error.message : 'No se pudo guardar. Revisa los usuarios.';

  return (
    <Card header="Vincular plataformas de ajedrez">
      <p style={{ fontSize: 12.5, color: 'var(--text-muted)', marginTop: -2, marginBottom: 12 }}>
        Vincula tu usuario de cada plataforma para traer tus ratings. Al guardar, sincronizamos los datos.
      </p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12 }}>
        <label style={{ flex: '1 1 220px', display: 'block' }}>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Usuario de Lichess</span>
          <input
            style={{ ...inputStyle, marginTop: 5 }}
            value={lichess}
            onChange={(e) => setLichess(e.target.value)}
            placeholder="tu-usuario"
            aria-label="Usuario de Lichess"
            autoComplete="off"
          />
        </label>
        <label style={{ flex: '1 1 220px', display: 'block' }}>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Usuario de Chess.com</span>
          <input
            style={{ ...inputStyle, marginTop: 5 }}
            value={chesscom}
            onChange={(e) => setChesscom(e.target.value)}
            placeholder="tu-usuario"
            aria-label="Usuario de Chess.com"
            autoComplete="off"
          />
        </label>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 14, flexWrap: 'wrap' }}>
        <Button
          variant="primary"
          loading={mutation.isPending}
          disabled={!dirty || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          Guardar y sincronizar
        </Button>
        {mutation.isSuccess && !dirty ? (
          <span style={{ fontSize: 12.5, color: 'var(--cq-accent, #6abf74)', fontWeight: 600 }} role="status">
            ✓ Guardado y sincronizado
          </span>
        ) : null}
        {mutation.isError ? (
          <span style={{ fontSize: 12.5, color: 'var(--cq-error, #e05a5a)', fontWeight: 600 }} role="alert">
            {errMsg}
          </span>
        ) : null}
      </div>
    </Card>
  );
};
