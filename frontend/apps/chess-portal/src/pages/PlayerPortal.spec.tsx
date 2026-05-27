import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PlayerPortalPage } from './PlayerPortal';

const navigateMock = vi.fn();

type QueryStub = {
  data: unknown;
  isLoading: boolean;
  isError: boolean;
  refetch: () => unknown;
};

const dashboardQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };
const ratingHistoryQuery: QueryStub = { data: null, isLoading: false, isError: false, refetch: vi.fn() };

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: ({ queryKey }: { queryKey: unknown[] }) =>
    queryKey[1] === 'rating-history' ? ratingHistoryQuery : dashboardQuery,
}));

vi.mock('@chessquery/ui-lib', () => ({
  ErrorAlert: ({ title, onRetry }: { title: string; onRetry?: () => void }) => (
    <div role="alert">
      {title}
      {onRetry && <button onClick={onRetry}>Reintentar</button>}
    </div>
  ),
  Skeleton: () => <div data-testid="skeleton" />,
}));

vi.mock('../api', () => ({
  playerApi: { dashboard: vi.fn(), ratingHistory: vi.fn() },
}));

const baseProfile = {
  id: 1,
  firstName: 'Ana',
  lastName: 'Vega',
  clubName: 'Lasker',
  countryName: 'Chile',
  countryFlag: '🇨🇱',
  lichessUsername: 'anav',
  eloFideStandard: 1900,
  eloNational: 1850,
};

describe('PlayerPortalPage', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    dashboardQuery.data = null;
    dashboardQuery.isLoading = false;
    dashboardQuery.isError = false;
    ratingHistoryQuery.data = null;
    ratingHistoryQuery.isLoading = false;
    ratingHistoryQuery.isError = false;
  });

  it('muestra skeletons mientras carga el dashboard', () => {
    dashboardQuery.isLoading = true;
    render(<PlayerPortalPage />);
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('muestra ErrorAlert cuando el dashboard falla', () => {
    dashboardQuery.isError = true;
    render(<PlayerPortalPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cargar tu portal');
  });

  it('renderiza el hero con nombre, club, país y rating ELO FIDE', () => {
    dashboardQuery.data = {
      profile: baseProfile,
      stats: { favoriteOpening: 'Siciliana' },
      recentGames: [],
    };
    render(<PlayerPortalPage />);
    expect(screen.getByRole('heading', { level: 1, name: /Ana Vega/i })).toBeInTheDocument();
    expect(screen.getByText(/Lasker/)).toBeInTheDocument();
    expect(screen.getByText(/Chile/)).toBeInTheDocument();
    expect(screen.getByText(/@anav/)).toBeInTheDocument();
    expect(screen.getByText('1900')).toBeInTheDocument();
    expect(screen.getByText('FIDE')).toBeInTheDocument();
  });

  it('usa ELO nacional cuando no hay FIDE', () => {
    dashboardQuery.data = {
      profile: { ...baseProfile, eloFideStandard: null },
      stats: {},
      recentGames: [],
    };
    render(<PlayerPortalPage />);
    expect(screen.getByText('1850')).toBeInTheDocument();
    expect(screen.getByText('NACIONAL')).toBeInTheDocument();
  });

  it('muestra CTA "Jugar mi primera partida" cuando no hay partidas', () => {
    dashboardQuery.data = {
      profile: baseProfile,
      stats: {},
      recentGames: [],
    };
    render(<PlayerPortalPage />);
    expect(screen.getByText(/Sin partidas todavía\./i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /Jugar mi primera partida/i }));
    expect(navigateMock).toHaveBeenCalledWith('/play');
  });

  it('renderiza las últimas partidas con resultado +/–/= según el bando', () => {
    dashboardQuery.data = {
      profile: baseProfile,
      stats: { favoriteOpening: 'Inglesa' },
      recentGames: [
        { id: 10, whitePlayerId: 1, blackPlayerId: 2, blackName: 'Rival', result: '1-0', playedAt: new Date().toISOString() },
        { id: 11, whitePlayerId: 3, blackPlayerId: 1, whiteName: 'Otro',  result: '1-0', playedAt: new Date().toISOString() },
        { id: 12, whitePlayerId: 1, blackPlayerId: 4, blackName: 'X',     result: '1/2-1/2', playedAt: new Date().toISOString() },
      ],
    };
    render(<PlayerPortalPage />);
    expect(screen.getByText(/Inglesa/)).toBeInTheDocument();
    expect(screen.getByText(/vs Rival/)).toBeInTheDocument();
    expect(screen.getByText(/vs Otro/)).toBeInTheDocument();
  });
});
