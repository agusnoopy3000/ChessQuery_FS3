import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PlayerMatchmakingPage } from './PlayerMatchmaking';

const navigateMock = vi.fn();
const mutateMock = vi.fn();

type MutationStub = {
  mutate: ReturnType<typeof vi.fn>;
  isPending: boolean;
  isError: boolean;
  error: unknown;
};

const mutationState: MutationStub = {
  mutate: mutateMock,
  isPending: false,
  isError: false,
  error: null,
};

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
}));

vi.mock('@tanstack/react-query', () => ({
  useMutation: (opts: { onSuccess?: (g: { id: number }) => void }) => {
    // Cuando el test invoca mutate, simulamos onSuccess con un game.id fijo.
    mutateMock.mockImplementation(() => opts.onSuccess?.({ id: 42 }));
    return mutationState;
  },
}));

vi.mock('@chessquery/ui-lib', () => ({
  Button: ({ children, onClick, loading }: {
    children: React.ReactNode; onClick?: () => void; loading?: boolean;
  }) => (
    <button onClick={onClick} disabled={loading}>{children}</button>
  ),
  Card: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ErrorAlert: ({ title, message }: { title: string; message?: string }) => (
    <div role="alert">{title}{message ? ` — ${message}` : ''}</div>
  ),
}));

vi.mock('../api', () => ({
  liveGameApi: { create: vi.fn() },
}));

describe('PlayerMatchmakingPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    mutateMock.mockReset();
    mutationState.isPending = false;
    mutationState.isError = false;
    mutationState.error = null;
  });

  it('renderiza el botón de empezar partida y la copy de la página', () => {
    render(<PlayerMatchmakingPage />);
    expect(screen.getByRole('heading', { name: /Empezar partida/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Empezar partida en vivo/i })).toBeInTheDocument();
  });

  it('navega a /play/{id} al crear la partida exitosamente', () => {
    render(<PlayerMatchmakingPage />);
    fireEvent.click(screen.getByRole('button', { name: /Empezar partida en vivo/i }));
    expect(mutateMock).toHaveBeenCalled();
    expect(navigateMock).toHaveBeenCalledWith('/play/42');
  });

  it('muestra ErrorAlert con el message del backend cuando la mutación falla', () => {
    mutationState.isError = true;
    mutationState.error = { response: { data: { message: 'Cuota excedida' } } };
    render(<PlayerMatchmakingPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Cuota excedida');
  });

  it('muestra "Error desconocido" si el error no trae message', () => {
    mutationState.isError = true;
    mutationState.error = {};
    render(<PlayerMatchmakingPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('Error desconocido');
  });

  it('deshabilita el botón mientras la mutación está pendiente', () => {
    mutationState.isPending = true;
    render(<PlayerMatchmakingPage />);
    expect(screen.getByRole('button', { name: /Empezar partida en vivo/i })).toBeDisabled();
  });
});
