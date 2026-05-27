import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { OrganizerPortalPage } from './OrganizerPortal';

const navigateMock = vi.fn();
const mutateMock = vi.fn();

type QueryStub = {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => unknown;
};

const tournamentsQuery: QueryStub = {
  data: { content: [] }, isLoading: false, isError: false, refetch: vi.fn(),
};
const createMutation = {
  mutate: mutateMock, isPending: false, isError: false, error: null,
};

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn().mockResolvedValue(undefined) }),
  useQuery: () => tournamentsQuery,
  useMutation: () => createMutation,
}));

vi.mock('@chessquery/ui-lib', () => ({
  ErrorAlert: ({ title }: { title: string }) => <div role="alert">{title}</div>,
  Skeleton: () => <div data-testid="skeleton" />,
}));

vi.mock('@chessquery/shared', () => ({
  useAuth: () => ({ user: { email: 'ana@demo.cl' } }),
}));

vi.mock('../api', () => ({
  organizerApi: { listTournaments: vi.fn(), createTournament: vi.fn() },
}));

vi.mock('../portal-utils', () => ({
  dedupeBy: <T,>(arr: T[]) => arr,
  formatDate: (d: string) => d,
  tournamentStatusVariant: () => 'default',
  unwrapContent: <T,>(data: unknown): T[] => {
    if (Array.isArray(data)) return data as T[];
    if (data && typeof data === 'object' && 'content' in data) return (data as { content: T[] }).content;
    return [];
  },
}));

vi.mock('../components/CreateTournamentModal', () => ({
  CreateTournamentModal: ({ open, onClose, onSubmit }: {
    open: boolean; onClose: () => void; onSubmit: (input: unknown) => void;
  }) => (
    open ? (
      <div role="dialog">
        <button onClick={() => onSubmit({ name: 'Nuevo' })}>Confirmar creación</button>
        <button onClick={onClose}>Cerrar</button>
      </div>
    ) : null
  ),
}));

describe('OrganizerPortalPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateMock.mockReset();
    tournamentsQuery.data = { content: [] };
    tournamentsQuery.isLoading = false;
    tournamentsQuery.isError = false;
    createMutation.isPending = false;
    createMutation.isError = false;
    createMutation.error = null;
  });

  it('saluda al organizador con el alias derivado del email', () => {
    render(<OrganizerPortalPage />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/Hola, ana/);
  });

  it('muestra los KPIs en cero cuando no hay torneos', () => {
    render(<OrganizerPortalPage />);
    expect(screen.getByText('TOTAL TORNEOS')).toBeInTheDocument();
    expect(screen.getAllByText('0').length).toBeGreaterThan(0);
  });

  it('agrega torneos al KPI total, abiertos, en curso y finalizados', () => {
    tournamentsQuery.data = {
      content: [
        { id: 1, name: 'A', status: 'OPEN', pending: 3, startDate: '2026-01-01' },
        { id: 2, name: 'B', status: 'IN_PROGRESS', pending: 1, startDate: '2026-01-01' },
        { id: 3, name: 'C', status: 'FINISHED', pending: 0, startDate: '2026-01-01' },
        { id: 4, name: 'D', status: 'DRAFT', pending: 0, startDate: '2026-01-01' },
      ],
    };
    render(<OrganizerPortalPage />);
    expect(screen.getByText('TOTAL TORNEOS')).toBeInTheDocument();
    // Total=4 y pendingApprovals=4 → ambos KPIs muestran "4".
    expect(screen.getAllByText('4').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('ABIERTOS')).toBeInTheDocument();
    expect(screen.getByText('EN CURSO')).toBeInTheDocument();
    expect(screen.getByText('FINALIZADOS')).toBeInTheDocument();
  });

  it('abre y cierra el modal de creación', () => {
    render(<OrganizerPortalPage />);
    fireEvent.click(screen.getByRole('button', { name: /Crear torneo/i }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Cerrar/i }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('dispara mutate al confirmar la creación', () => {
    render(<OrganizerPortalPage />);
    fireEvent.click(screen.getByRole('button', { name: /Crear torneo/i }));
    fireEvent.click(screen.getByRole('button', { name: /Confirmar creación/i }));
    expect(mutateMock).toHaveBeenCalledWith({ name: 'Nuevo' });
  });
});
