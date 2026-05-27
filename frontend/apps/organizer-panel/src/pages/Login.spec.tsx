import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LoginPage } from './Login';

const navigateMock = vi.fn();
const loginMock = vi.fn();
let searchParams = new URLSearchParams('');

vi.mock('react-router-dom', () => ({
  Link: ({ to, children }: { to: string; children: React.ReactNode }) => <a href={to}>{children}</a>,
  useNavigate: () => navigateMock,
  useSearchParams: () => [searchParams],
}));

vi.mock('@chessquery/shared', () => ({
  useAuth: () => ({ login: loginMock }),
  translateAuthError: (raw: string | undefined, fallback: string) => raw ?? fallback,
}));

describe('Organizer LoginPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    loginMock.mockReset();
    searchParams = new URLSearchParams('');
  });

  it('renderiza el formulario y botón de iniciar sesión', () => {
    render(<LoginPage />);
    expect(screen.getByRole('button', { name: /Entrar al panel/i })).toBeInTheDocument();
  });

  it('muestra error de email inválido al submitear con formato malo', async () => {
    const { container } = render(<LoginPage />);
    const email = container.querySelector('input[name="email"]') as HTMLInputElement;
    const pass = container.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(email, { target: { name: 'email', value: 'sin-arroba' } });
    fireEvent.change(pass, { target: { name: 'password', value: 'algo' } });
    fireEvent.submit(container.querySelector('form')!);
    await waitFor(() => {
      expect(screen.getByText(/Email inválido/i)).toBeInTheDocument();
    });
    expect(loginMock).not.toHaveBeenCalled();
  });

  it('navega a "/" tras login exitoso sin parámetro next', async () => {
    loginMock.mockResolvedValue({});
    const { container } = render(<LoginPage />);
    const email = container.querySelector('input[name="email"]') as HTMLInputElement;
    const pass = container.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(email, { target: { name: 'email', value: 'org@demo.cl' } });
    fireEvent.change(pass, { target: { name: 'password', value: 'secreto1' } });
    fireEvent.submit(container.querySelector('form')!);
    await waitFor(() => {
      expect(loginMock).toHaveBeenCalledWith('org@demo.cl', 'secreto1');
      expect(navigateMock).toHaveBeenCalledWith('/');
    });
  });

  it('respeta el parámetro next decodificado en la redirección', async () => {
    searchParams = new URLSearchParams('next=%2Ftournaments%2F5');
    loginMock.mockResolvedValue({});
    const { container } = render(<LoginPage />);
    const email = container.querySelector('input[name="email"]') as HTMLInputElement;
    const pass = container.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(email, { target: { name: 'email', value: 'org@demo.cl' } });
    fireEvent.change(pass, { target: { name: 'password', value: 'secreto1' } });
    fireEvent.submit(container.querySelector('form')!);
    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/tournaments/5');
    });
  });

  it('muestra error global del backend cuando login falla', async () => {
    loginMock.mockRejectedValue({ response: { data: { message: 'Credenciales inválidas' } } });
    const { container } = render(<LoginPage />);
    const email = container.querySelector('input[name="email"]') as HTMLInputElement;
    const pass = container.querySelector('input[name="password"]') as HTMLInputElement;
    fireEvent.change(email, { target: { name: 'email', value: 'org@demo.cl' } });
    fireEvent.change(pass, { target: { name: 'password', value: 'incorrecta' } });
    fireEvent.submit(container.querySelector('form')!);
    await waitFor(() => {
      expect(screen.getByText(/Credenciales inválidas/i)).toBeInTheDocument();
    });
    // El campo password se limpia.
    expect((container.querySelector('input[name="password"]') as HTMLInputElement).value).toBe('');
  });
});
