import { Player, Role, Tournament } from '@chessquery/shared';

export const getDefaultRoute = (role?: Role | null) => {
  if (role === 'ORGANIZER') return '/organizer';
  if (role === 'ADMIN') return '/admin';
  return '/portal';
};

export const resolveRequestedRoute = (role: Role | undefined, requested?: string | null) => {
  if (!requested || !requested.startsWith('/')) {
    return getDefaultRoute(role);
  }

  if (role === 'PLAYER' && (requested === '/portal' || requested === '/play' || requested === '/player/me')) {
    return requested;
  }

  if (role === 'ORGANIZER' && (requested === '/organizer' || requested === '/organizer/players' || requested === '/organizer/tournaments')) {
    return requested;
  }

  if (role === 'ADMIN' && (requested === '/admin' || requested === '/admin/etl')) {
    return requested;
  }

  if (requested === '/search' || requested === '/rankings' || requested === '/tournaments') {
    return requested;
  }

  return getDefaultRoute(role);
};

export const getRoleLabel = (role?: Role | null) => {
  if (role === 'ORGANIZER') return 'Organizador';
  if (role === 'ADMIN') return 'Administrador';
  return 'Jugador';
};

export const getRoleSummary = (role?: Role | null) => {
  if (role === 'ORGANIZER') return 'Clubes, academias y entidades con foco en gestión competitiva.';
  if (role === 'ADMIN') return 'Operación, sincronización de fuentes y observabilidad de la plataforma.';
  return 'Competencia, perfiles, ranking y actividad conectada al ecosistema Lichess.';
};

export const buildPlayerName = (player?: Partial<Player> | null) =>
  [player?.firstName, player?.lastName].filter(Boolean).join(' ') || 'Jugador sin nombre';

export const getPrimaryRating = (player?: Partial<Player> | null) =>
  player?.eloFideStandard ??
  player?.eloNational ??
  player?.eloFideRapid ??
  player?.eloFideBlitz ??
  player?.eloPlatform ??
  null;

export const formatDate = (value?: string | null) => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(parsed);
};

export const formatDateTime = (value?: string | null) => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat('es-CL', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(parsed);
};

export const formatNumber = (value?: number | null) =>
  typeof value === 'number' ? new Intl.NumberFormat('es-CL').format(value) : '—';

export const tournamentStatusVariant = (status?: Tournament['status'] | string | null) => {
  if (status === 'IN_PROGRESS') return 'success' as const;
  if (status === 'OPEN') return 'info' as const;
  if (status === 'FINISHED') return 'neutral' as const;
  return 'warning' as const;
};

export const circuitStateVariant = (state?: string | null) => {
  if (state === 'CLOSED') return 'success' as const;
  if (state === 'HALF_OPEN') return 'warning' as const;
  if (state === 'OPEN') return 'danger' as const;
  return 'neutral' as const;
};

export const unwrapContent = <T,>(data: unknown): T[] => {
  if (Array.isArray(data)) return data as T[];
  const maybePage = data as { content?: T[] } | null | undefined;
  return maybePage?.content ?? [];
};

export const buildLichessProfileUrl = (username?: string | null) =>
  username ? `https://lichess.org/@/${encodeURIComponent(username)}` : null;

export const etlSources = [
  {
    key: 'FIDE',
    label: 'FIDE Database',
    icon: '♙',
    description: 'Ratings oficiales y referencia internacional.',
  },
  {
    key: 'LICHESS',
    label: 'Lichess API',
    icon: '♖',
    description: 'Actividad, presencia competitiva y usuarios conectados al ecosistema online.',
  },
  {
    key: 'AJEFECH',
    label: 'AJEFECH',
    icon: '♘',
    description: 'Fuentes federativas y actividad nacional complementaria.',
  },
  {
    key: 'CHESS_RESULTS',
    label: 'Chess Results',
    icon: '♔',
    description: 'Resultados históricos y publicación de competencias.',
  },
];
