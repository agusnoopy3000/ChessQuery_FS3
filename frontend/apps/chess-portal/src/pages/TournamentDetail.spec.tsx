import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TournamentDetailPage } from './TournamentDetail';

const navigateMock = vi.fn();
const mutateMock = vi.fn();

type QueryStub = {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => unknown;
};

const detailQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const myRegQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const standingsQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const roundQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const registerMutation = { mutate: mutateMock, isPending: false, isError: false, error: null };

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ id: '7' }),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn().mockResolvedValue(undefined) }),
  useQuery: ({ queryKey }: { queryKey: unknown[] }) => {
    if (queryKey.includes('my-registration')) return myRegQuery;
    if (queryKey.includes('standings'))       return standingsQuery;
    if (queryKey.includes('round'))           return roundQuery;
    return detailQuery;
  },
  useMutation: () => registerMutation,
}));

vi.mock('@chessquery/ui-lib', () => ({
  Button: ({ children, onClick, disabled, loading }: {
    children: React.ReactNode; onClick?: () => void; disabled?: boolean; loading?: boolean;
  }) => (
    <button onClick={onClick} disabled={disabled || loading}>{children}</button>
  ),
  Card: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Badge: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
  Skeleton: () => <div data-testid="skeleton" />,
  ErrorAlert: ({ title, onRetry }: { title: string; onRetry?: () => void }) => (
    <div role="alert">
      {title}
      {onRetry && <button onClick={onRetry}>Reintentar</button>}
    </div>
  ),
  EmptyState: ({ title }: { title: string }) => <div>{title}</div>,
  StandingsTable: ({ standings }: { standings: Array<{ playerName?: string }> }) => (
    <div data-testid="standings">{standings?.length ?? 0}</div>
  ),
  Table: ({ rows }: { rows: unknown[] }) => <div>rows:{rows?.length ?? 0}</div>,
  TableColumn: () => null,
}));

vi.mock('../api', () => ({
  tournamentApi: {
    detail: vi.fn(), myRegistration: vi.fn(), register: vi.fn(),
    standings: vi.fn(), round: vi.fn(),
  },
}));

const baseTournament = {
  id: 7, name: 'Open Verano',
  status: 'OPEN', format: 'SWISS', rounds: 7,
  startDate: '2026-07-01', endDate: '2026-07-07',
  location: 'Santiago', registered: 12, maxPlayers: 32,
};

describe('TournamentDetailPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateMock.mockReset();
    detailQuery.data = null;
    detailQuery.isLoading = false;
    detailQuery.isError = false;
    myRegQuery.data = null;
    myRegQuery.isLoading = false;
    registerMutation.isPending = false;
    registerMutation.isError = false;
    registerMutation.error = null;
  });

  it('muestra skeletons mientras carga', () => {
    detailQuery.isLoading = true;
    render(<TournamentDetailPage />);
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('muestra ErrorAlert cuando el torneo no existe', () => {
    detailQuery.isError = true;
    render(<TournamentDetailPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Torneo no encontrado');
  });

  it('renderiza nombre, status y fechas del torneo', () => {
    detailQuery.data = baseTournament;
    render(<TournamentDetailPage />);
    expect(screen.getByRole('heading', { level: 1, name: 'Open Verano' })).toBeInTheDocument();
    expect(screen.getByText('OPEN')).toBeInTheDocument();
    expect(screen.getByText('SWISS')).toBeInTheDocument();
    expect(screen.getByText('7 rondas')).toBeInTheDocument();
  });

  it('habilita CTA de inscripción cuando el torneo está OPEN y no hay registration', () => {
    detailQuery.data = baseTournament;
    render(<TournamentDetailPage />);
    const btn = screen.getByRole('button', { name: /Inscribirme/i });
    expect(btn).not.toBeDisabled();
    fireEvent.click(btn);
    expect(mutateMock).toHaveBeenCalled();
  });

  it('deshabilita CTA cuando el torneo está DRAFT', () => {
    detailQuery.data = { ...baseTournament, status: 'DRAFT' };
    render(<TournamentDetailPage />);
    expect(screen.getByRole('button', { name: /Inscribirme/i })).toBeDisabled();
  });

  it('muestra estado "Inscripción confirmada" cuando myRegistration es CONFIRMED', () => {
    detailQuery.data = baseTournament;
    myRegQuery.data = { status: 'CONFIRMED' };
    render(<TournamentDetailPage />);
    expect(screen.getByText(/Inscripción confirmada/i)).toBeInTheDocument();
  });

  it('muestra estado "Inscripción pendiente" cuando myRegistration es PENDING', () => {
    detailQuery.data = baseTournament;
    myRegQuery.data = { status: 'PENDING' };
    render(<TournamentDetailPage />);
    expect(screen.getByText(/Inscripción pendiente/i)).toBeInTheDocument();
  });

  it('navega de vuelta a /tournaments al click en "← Volver a torneos"', () => {
    detailQuery.data = baseTournament;
    render(<TournamentDetailPage />);
    fireEvent.click(screen.getByRole('button', { name: /Volver a torneos/i }));
    expect(navigateMock).toHaveBeenCalledWith('/tournaments');
  });
});
