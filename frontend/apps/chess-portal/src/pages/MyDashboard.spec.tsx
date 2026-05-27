import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MyDashboardPage } from './MyDashboard';

type QueryStub = {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => unknown;
};

const dashboardQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const lichessQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };

vi.mock('@tanstack/react-query', () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) =>
    Array.isArray(queryKey) && queryKey[1] === 'lichess' ? lichessQuery : dashboardQuery,
}));

vi.mock('@chessquery/ui-lib', () => {
  const passthrough = (props: { children?: React.ReactNode; header?: string }) => (
    <div data-testid="card">
      {props.header ? <div>{props.header}</div> : null}
      {props.children}
    </div>
  );
  return {
    Card: passthrough,
    RatingBadge: ({ rating, label }: { rating: number; label: string }) => (
      <div data-testid="rating">{label}:{rating}</div>
    ),
    Skeleton: () => <div data-testid="skeleton" />,
    ErrorAlert: ({ title }: { title: string }) => <div role="alert">{title}</div>,
    EmptyState: ({ title }: { title: string }) => <div>{title}</div>,
  };
});

vi.mock('../api', () => ({
  playerApi: { dashboard: vi.fn(), lichess: vi.fn() },
}));

describe('MyDashboardPage', () => {
  beforeEach(() => {
    dashboardQuery.data = null;
    dashboardQuery.isLoading = false;
    dashboardQuery.isError = false;
    lichessQuery.data = null;
    lichessQuery.isLoading = false;
    lichessQuery.isError = false;
  });

  it('muestra skeletons mientras el dashboard carga', () => {
    dashboardQuery.isLoading = true;
    render(<MyDashboardPage />);
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('muestra ErrorAlert si el dashboard falla', () => {
    dashboardQuery.isError = true;
    render(<MyDashboardPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar tu dashboard');
  });

  it('renderiza nombre, ELO nacional y FIDE cuando hay datos', () => {
    dashboardQuery.data = {
      profile: {
        id: 1, firstName: 'Ana', lastName: 'Vega',
        lichessUsername: null, eloNational: 1850, eloFideStandard: 1900, clubName: 'Lasker',
      },
    };
    render(<MyDashboardPage />);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Ana Vega');
    expect(screen.getByText('NAC:1850')).toBeInTheDocument();
    expect(screen.getByText('FIDE:1900')).toBeInTheDocument();
    expect(screen.getByText('Lasker')).toBeInTheDocument();
  });

  it('muestra EmptyState cuando el jugador no tiene cuenta Lichess vinculada', () => {
    dashboardQuery.data = {
      profile: { id: 2, firstName: 'B', lastName: 'C', lichessUsername: null,
                 eloNational: null, eloFideStandard: null, clubName: null },
    };
    render(<MyDashboardPage />);
    expect(screen.getByText(/Sin cuenta Lichess vinculada/i)).toBeInTheDocument();
  });

  it('renderiza los ratings de Lichess cuando vienen del API', () => {
    dashboardQuery.data = {
      profile: { id: 3, firstName: 'C', lastName: 'D', lichessUsername: 'cd_lichess',
                 eloNational: null, eloFideStandard: null, clubName: null },
    };
    lichessQuery.data = {
      found: true,
      ratings: [
        { variant: 'bullet', rating: 1700, games: 50, prog: 12 },
        { variant: 'blitz',  rating: 1820, games: 200, prog: -8 },
      ],
    };
    render(<MyDashboardPage />);
    expect(screen.getByText('LICHESS:1820')).toBeInTheDocument();
    expect(screen.getByText('Bullet')).toBeInTheDocument();
    expect(screen.getByText('Blitz')).toBeInTheDocument();
  });

  it('muestra EmptyState "no encontrado" si lichess.found es false', () => {
    dashboardQuery.data = {
      profile: { id: 4, firstName: 'E', lastName: 'F', lichessUsername: 'noexisto',
                 eloNational: null, eloFideStandard: null, clubName: null },
    };
    lichessQuery.data = { found: false, error: 'Usuario no existe' };
    render(<MyDashboardPage />);
    expect(screen.getByText(/Usuario @noexisto no encontrado/i)).toBeInTheDocument();
  });
});
