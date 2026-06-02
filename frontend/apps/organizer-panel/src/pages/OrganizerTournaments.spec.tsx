import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

// El tablero espectador importa el cliente Supabase (se instancia al cargar el
// módulo y requiere VITE_*). No se ejercita en este test → lo mockeamos para no
// arrastrar esa dependencia (en CI no hay .env).
vi.mock('../components/LiveSpectatorModal', () => ({
  LiveSpectatorModal: () => null,
}));

// Mocks de los componentes de UI: los reemplazamos por placeholders mínimos
// para no depender de su implementación (la página tiene 700 LOC con muchísimas
// dependencias visuales que no aportan al test).
vi.mock('@chessquery/ui-lib', () => ({
  Badge: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  Button: ({ children, onClick }: { children: ReactNode; onClick?: () => void }) => (
    <button onClick={onClick}>{children}</button>
  ),
  Card: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  EmptyState: ({ title, description }: { title: string; description?: string }) => (
    <div data-testid="empty-state">
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  ),
  ErrorAlert: ({ title, onRetry }: { title: string; onRetry?: () => void }) => (
    <div data-testid="error-alert">
      <p>{title}</p>
      {onRetry && <button onClick={onRetry}>Reintentar</button>}
    </div>
  ),
  Select: ({ children, value, onChange }: { children: ReactNode; value?: string; onChange?: (v: string) => void }) => (
    <select value={value} onChange={(e) => onChange?.(e.target.value)}>{children}</select>
  ),
  Skeleton: () => <div data-testid="skeleton" />,
  StandingsTable: () => <div />,
  Table: <T,>({ data }: { data: T[] }) => <div data-testid="table" data-rows={data?.length ?? 0} />,
}));

vi.mock('../components/CreateTournamentModal', () => ({
  CreateTournamentModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="create-modal-open" /> : null,
}));

vi.mock('../components/RegistrationsPanel', () => ({
  RegistrationsPanel: () => <div data-testid="registrations-panel" />,
}));

// Mockeamos la API. listTournaments es la pieza crítica de los 3 casos
// que vamos a verificar.
const listTournamentsMock = vi.fn();
const createTournamentMock = vi.fn();
vi.mock('../api', () => ({
  organizerApi: {
    listTournaments: (...args: unknown[]) => listTournamentsMock(...args),
    tournamentStandings: vi.fn().mockResolvedValue([]),
    round: vi.fn().mockResolvedValue({ pairings: [] }),
    listRegistrations: vi.fn().mockResolvedValue([]),
    generateRound: vi.fn(),
    patchPairingResult: vi.fn(),
    createTournament: (...args: unknown[]) => createTournamentMock(...args),
    patchTournamentStatus: vi.fn(),
    approveRegistration: vi.fn(),
    rejectRegistration: vi.fn(),
    deleteTournament: vi.fn(),
  },
}));

import { OrganizerTournamentsPage } from './OrganizerTournaments';

const renderPage = () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <OrganizerTournamentsPage />
    </QueryClientProvider>,
  );
};

describe('OrganizerTournamentsPage', () => {
  beforeEach(() => {
    listTournamentsMock.mockReset();
    createTournamentMock.mockReset();
  });

  it('muestra EmptyState cuando la lista vuelve vacía', async () => {
    listTournamentsMock.mockResolvedValue({ content: [] });
    renderPage();
    await waitFor(() => {
      expect(screen.getAllByTestId('empty-state').length).toBeGreaterThan(0);
    });
  });

  it('muestra ErrorAlert cuando la query falla', async () => {
    listTournamentsMock.mockRejectedValue(new Error('upstream down'));
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('error-alert')).toBeInTheDocument();
    });
  });

  it('abre el modal al clickear "Crear torneo"', async () => {
    listTournamentsMock.mockResolvedValue({ content: [] });
    renderPage();
    await waitFor(() => expect(listTournamentsMock).toHaveBeenCalled());

    // El botón "Crear torneo" está varias veces; buscamos el primero que matchea.
    const createBtns = await screen.findAllByRole('button', { name: /Crear torneo/i });
    fireEvent.click(createBtns[0]);
    await waitFor(() => {
      expect(screen.getByTestId('create-modal-open')).toBeInTheDocument();
    });
  });
});
