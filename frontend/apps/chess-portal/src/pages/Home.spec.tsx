import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { HomePage } from './Home';

// Mock de react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: () => vi.fn(),
}));

describe('HomePage', () => {
  it('debe renderizar el título principal', () => {
    render(<HomePage />);
    expect(screen.getByRole('heading', { name: /Compite, organiza y juega en tiempo real/i })).toBeInTheDocument();
  });

  it('debe mostrar los botones de llamada a la acción', () => {
    render(<HomePage />);
    expect(screen.getByRole('button', { name: /Crear cuenta/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Iniciar sesión/i })).toBeInTheDocument();
  });
});
