import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { RegisterPage } from './Register';

const navigateMock = vi.fn();
const registerMock = vi.fn();

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
  useSearchParams: () => [new URLSearchParams('')],
}));

vi.mock('@chessquery/shared', () => ({
  useAuth: () => ({ register: registerMock }),
  translateAuthError: (raw: string | undefined, fallback: string) => raw ?? fallback,
}));

const toggleTerms = () => {
  const label = screen.getByText(/Acepto los/i).closest('label')!;
  const box = label.querySelector('div')!;
  fireEvent.click(box);
};

const fillBaseFields = () => {
  fireEvent.change(document.querySelector('input[name="nombre"]')!, { target: { name: 'nombre', value: 'Ana' } });
  fireEvent.change(document.querySelector('input[name="apellido"]')!, { target: { name: 'apellido', value: 'Soto' } });
  fireEvent.change(document.querySelector('input[name="email"]')!, { target: { name: 'email', value: 'ana@test.cl' } });
  fireEvent.change(document.querySelector('input[name="password"]')!, { target: { name: 'password', value: 'secreto12' } });
  fireEvent.change(document.querySelector('input[name="confirmPassword"]')!, { target: { name: 'confirmPassword', value: 'secreto12' } });
};

describe('RegisterPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    registerMock.mockReset();
  });

  it('renderiza el formulario de registro', () => {
    render(<RegisterPage />);
    expect(screen.getByRole('heading', { name: /Crear cuenta/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Crear cuenta jugador/i })).toBeInTheDocument();
  });

  it('cambia el botón al elegir rol Organizador', () => {
    render(<RegisterPage />);
    fireEvent.click(screen.getByText(/Organizador/i));
    expect(screen.getByRole('button', { name: /Crear cuenta organizador/i })).toBeInTheDocument();
  });

  it('valida contraseñas que no coinciden', async () => {
    render(<RegisterPage />);
    fillBaseFields();
    fireEvent.change(document.querySelector('input[name="confirmPassword"]')!, {
      target: { name: 'confirmPassword', value: 'otra1234' },
    });
    // Aceptar términos para llegar a la validación de password
    toggleTerms();
    fireEvent.click(screen.getByRole('button', { name: /Crear cuenta jugador/i }));
    await waitFor(() => {
      expect(screen.getByText(/Las contraseñas no coinciden/i)).toBeInTheDocument();
    });
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('exige aceptar términos antes de registrar', async () => {
    render(<RegisterPage />);
    fillBaseFields();
    fireEvent.click(screen.getByRole('button', { name: /Crear cuenta jugador/i }));
    await waitFor(() => {
      expect(screen.getByText(/Debes aceptar los términos/i)).toBeInTheDocument();
    });
    expect(registerMock).not.toHaveBeenCalled();
  });

  it('muestra el error del servidor cuando el email ya está en uso', async () => {
    registerMock.mockRejectedValue({ message: 'User already registered' });
    render(<RegisterPage />);
    fillBaseFields();
    toggleTerms();
    fireEvent.click(screen.getByRole('button', { name: /Crear cuenta jugador/i }));
    await waitFor(() => {
      // El traductor de auth-errors devuelve raw cuando no hay match conocido
      // (en el mock translateAuthError devuelve el raw tal cual).
      expect(screen.getByText(/User already registered/i)).toBeInTheDocument();
    });
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it('llama a register, muestra éxito y navega cuando los datos son válidos', async () => {
    registerMock.mockResolvedValue({});
    render(<RegisterPage />);
    fillBaseFields();
    toggleTerms();
    fireEvent.click(screen.getByRole('button', { name: /Crear cuenta jugador/i }));
    // 1) register se llama con los datos correctos
    await waitFor(() => {
      expect(registerMock).toHaveBeenCalledWith(
        expect.objectContaining({
          email: 'ana@test.cl',
          password: 'secreto12',
          firstName: 'Ana',
          lastName: 'Soto',
          role: 'PLAYER',
        }),
      );
    });
    // 2) se muestra la pantalla de éxito con animación
    await waitFor(() => {
      expect(screen.getByText(/Cuenta creada con éxito/i)).toBeInTheDocument();
    });
    // 3) la navegación ocurre de forma diferida (tras la confirmación)
    await waitFor(() => {
      expect(navigateMock).toHaveBeenCalledWith('/portal');
    }, { timeout: 2500 });
  });
});
