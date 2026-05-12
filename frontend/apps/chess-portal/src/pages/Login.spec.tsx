import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LoginPage } from './Login';

const navigateMock = vi.fn();
const loginMock = vi.fn();
const prefetchMock = vi.fn();

vi.mock('react-router-dom', () => ({
  Link: ({ to, children, ...rest }: { to: string; children: React.ReactNode }) => (
    <a href={to} {...rest}>
      {children}
    </a>
  ),
  useNavigate: () => navigateMock,
  useSearchParams: () => [new URLSearchParams('')],
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({ prefetchQuery: prefetchMock }),
}));

vi.mock('@chessquery/shared', () => ({
  useAuth: () => ({ login: loginMock }),
}));

vi.mock('../api', () => ({
  playerApi: { dashboard: vi.fn().mockResolvedValue({ profile: { id: 1 } }) },
}));

describe('LoginPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    loginMock.mockReset();
    prefetchMock.mockReset();
  });

  it('renderiza el formulario de inicio de sesión', () => {
    render(<LoginPage />);
    expect(screen.getByRole('heading', { name: /Ingresa a tu cuenta/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Iniciar sesión/i })).toBeInTheDocument();
  });

  it('muestra error si el email es inválido', async () => {
    const { container } = render(<LoginPage />);
    const emailInput = screen.getAllByRole('textbox')[0];
    const passwordInput = container.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(emailInput, { target: { name: 'email', value: 'no-es-email' } });
    fireEvent.change(passwordInput, { target: { name: 'password', value: 'algo' } });
    fireEvent.submit(container.querySelector('form')!);
    await waitFor(() => {
      expect(screen.getByText(/Email inválido/i)).toBeInTheDocument();
    });
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('llama a login y navega cuando las credenciales son válidas', async () => {
    loginMock.mockResolvedValue({ supabaseUserId: 'uid-1' });
    render(<LoginPage />);
    const emailInput = screen.getAllByRole('textbox')[0];
    fireEvent.change(emailInput, { target: { name: 'email', value: 'user@test.cl' } });
    const passwordInput = document.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { name: 'password', value: 'secreto1' } });
    fireEvent.click(screen.getByRole('button', { name: /Iniciar sesión/i }));
    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith('user@test.cl', 'secreto1');
      expect(navigateMock).toHaveBeenCalled();
    });
  });

  it('muestra mensaje cuando login lanza error', async () => {
    loginMock.mockRejectedValue({ message: 'Credenciales inválidas' });
    render(<LoginPage />);
    const emailInput = screen.getAllByRole('textbox')[0];
    fireEvent.change(emailInput, { target: { name: 'email', value: 'user@test.cl' } });
    const passwordInput = document.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(passwordInput, { target: { name: 'password', value: 'mala' } });
    fireEvent.click(screen.getByRole('button', { name: /Iniciar sesión/i }));
    await waitFor(() => {
      expect(screen.getByText(/Credenciales inválidas/i)).toBeInTheDocument();
    });
  });
});
