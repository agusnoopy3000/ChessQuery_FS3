import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

// Mocks de los componentes de UI: los reemplazamos por placeholders mínimos
// para no depender de su implementación (la página tiene 700 LOC con muchísimas
// dependencias visuales que no aportan al test). Cada mock conserva *solo* las
// props que la página usa para tomar decisiones de lógica (disabled, options,
// rows, header…) — lo demás (estilos inline, handlers de hover) se ignora.
vi.mock('@chessquery/ui-lib', () => ({
  Badge: ({ children }: { children: ReactNode }) => <span>{children}</span>,
  Button: ({
    children,
    onClick,
    disabled,
  }: {
    children: ReactNode;
    onClick?: () => void;
    disabled?: boolean;
  }) => (
    <button onClick={onClick} disabled={disabled}>
      {children}
    </button>
  ),
  // Renderizamos `header` además de `children`: el nombre del torneo
  // seleccionado vive en el header de la Card de detalle.
  Card: ({ children, header }: { children: ReactNode; header?: ReactNode }) => (
    <div>
      {header}
      {children}
    </div>
  ),
  EmptyState: ({ title, description }: { title: string; description?: string }) => (
    <div data-testid="empty-state">
      <h3>{title}</h3>
      {description && <p>{description}</p>}
    </div>
  ),
  ErrorAlert: ({ title, message, onRetry }: { title: string; message?: string; onRetry?: () => void }) => (
    <div data-testid="error-alert">
      <p>{title}</p>
      {message && <span>{message}</span>}
      {onRetry && <button onClick={onRetry}>Reintentar</button>}
    </div>
  ),
  // El Select real usa la prop `options`, no children. onChange recibe el evento.
  Select: ({
    value,
    onChange,
    options,
    label,
  }: {
    value?: string;
    onChange?: (e: { target: { value: string } }) => void;
    options?: Array<{ value: string; label: string }>;
    label?: string;
  }) => (
    <select aria-label={label} value={value} onChange={onChange}>
      {(options ?? []).map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
  ),
  Skeleton: () => <div data-testid="skeleton" />,
  StandingsTable: ({ entries }: { entries?: unknown[] }) => (
    <div data-testid="standings-table" data-rows={entries?.length ?? 0} />
  ),
  // El componente real pasa `rows` (no `data`) y `columns` con funciones `render`.
  // Invocamos cada `render(row)` para ejercitar las celdas (Select de resultado +
  // botón Guardar), igual que haría la tabla real.
  Table: <T,>({
    rows,
    columns,
    rowKey,
  }: {
    rows?: T[];
    columns?: Array<{ render?: (row: T) => ReactNode }>;
    rowKey?: (row: T) => string | number;
  }) => (
    <div data-testid="table" data-rows={rows?.length ?? 0}>
      {rows?.map((row, i) => (
        <div key={rowKey ? rowKey(row) : i} data-testid="table-row">
          {columns?.map((col, j) => <span key={j}>{col.render ? col.render(row) : null}</span>)}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('../components/CreateTournamentModal', () => ({
  // El modal real expone un formulario; aquí basta con un botón que dispare
  // onSubmit y un span que refleje el error, para cubrir las ramas de creación.
  CreateTournamentModal: ({
    open,
    onSubmit,
    error,
  }: {
    open: boolean;
    onSubmit?: (input: unknown) => void;
    error?: string | null;
  }) =>
    open ? (
      <div data-testid="create-modal-open">
        {error && <span data-testid="modal-error">{error}</span>}
        <button onClick={() => onSubmit?.({ name: 'Nuevo Torneo', format: 'SWISS' })}>
          submit-create
        </button>
      </div>
    ) : null,
}));

vi.mock('../components/RegistrationsPanel', () => ({
  RegistrationsPanel: () => <div data-testid="registrations-panel" />,
}));

// Mockeamos la API. Cada método que verificamos se expone como un mock
// controlable a nivel de módulo (mismo patrón que listTournamentsMock).
const listTournamentsMock = vi.fn();
const createTournamentMock = vi.fn();
const standingsMock = vi.fn();
const roundMock = vi.fn();
const generateRoundMock = vi.fn();
const patchStatusMock = vi.fn();
const deleteTournamentMock = vi.fn();
const listRegistrationsMock = vi.fn();
const patchPairingResultMock = vi.fn();

vi.mock('../api', () => ({
  organizerApi: {
    listTournaments: (...args: unknown[]) => listTournamentsMock(...args),
    tournamentStandings: (...args: unknown[]) => standingsMock(...args),
    round: (...args: unknown[]) => roundMock(...args),
    listRegistrations: (...args: unknown[]) => listRegistrationsMock(...args),
    generateRound: (...args: unknown[]) => generateRoundMock(...args),
    patchPairingResult: (...args: unknown[]) => patchPairingResultMock(...args),
    createTournament: (...args: unknown[]) => createTournamentMock(...args),
    patchTournamentStatus: (...args: unknown[]) => patchStatusMock(...args),
    approveRegistration: vi.fn(),
    rejectRegistration: vi.fn(),
    deleteTournament: (...args: unknown[]) => deleteTournamentMock(...args),
  },
}));

import { OrganizerTournamentsPage } from './OrganizerTournaments';

// Fábrica de torneos: solo los campos que la página lee. El resto del tipo
// Tournament no se ejercita, así que mantenemos el dato mínimo y legible.
const t = (over: Record<string, unknown> = {}) => ({
  id: 1,
  name: 'Open Verano',
  format: 'SWISS',
  status: 'DRAFT',
  startDate: '2026-07-01',
  registered: 4,
  maxPlayers: 16,
  rounds: 5,
  location: 'Santiago',
  ...over,
});

// Set de 4 torneos, uno por estado, para KPIs/filtros/transiciones.
const FOUR = [
  t({ id: 1, name: 'Open Verano', status: 'DRAFT', format: 'SWISS' }),
  t({ id: 2, name: 'Clausura Primavera', status: 'OPEN', format: 'ROUND_ROBIN' }),
  t({ id: 3, name: 'Copa Invierno', status: 'IN_PROGRESS', format: 'KNOCKOUT' }),
  t({ id: 4, name: 'Liga Otono', status: 'FINISHED', format: 'SWISS' }),
];

const renderPage = () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <OrganizerTournamentsPage />
    </QueryClientProvider>,
  );
};

// Selecciona un torneo de la lista clickeando su tarjeta (surface-button).
const selectCard = async (name: RegExp) => {
  const card = await screen.findByRole('button', { name });
  fireEvent.click(card);
};

describe('OrganizerTournamentsPage', () => {
  beforeEach(() => {
    listTournamentsMock.mockReset();
    createTournamentMock.mockReset();
    standingsMock.mockReset().mockResolvedValue([]);
    roundMock.mockReset().mockResolvedValue({ pairings: [] });
    generateRoundMock.mockReset().mockResolvedValue({ pairings: [] });
    patchStatusMock.mockReset().mockResolvedValue({});
    deleteTournamentMock.mockReset().mockResolvedValue(undefined);
    listRegistrationsMock.mockReset().mockResolvedValue([]);
    patchPairingResultMock.mockReset().mockResolvedValue({});
  });

  // ── Estados base (loading/error/empty) ──────────────────────────────────
  it('muestra EmptyState cuando la lista vuelve vacía', async () => {
    listTournamentsMock.mockResolvedValue({ content: [] });
    renderPage();
    await waitFor(() => {
      expect(screen.getAllByTestId('empty-state').length).toBeGreaterThan(0);
    });
  });

  it('muestra ErrorAlert cuando la query falla y permite reintentar', async () => {
    listTournamentsMock.mockRejectedValueOnce(new Error('upstream down'));
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('No se pudo cargar el listado')).toBeInTheDocument();
    });
    // Click en "Reintentar" → refetch (rama onRetry del ErrorAlert del listado).
    fireEvent.click(screen.getByText('Reintentar'));
    await screen.findByRole('button', { name: /Open Verano/ });
  });

  it('abre el modal al clickear "Crear torneo"', async () => {
    listTournamentsMock.mockResolvedValue({ content: [] });
    renderPage();
    await waitFor(() => expect(listTournamentsMock).toHaveBeenCalled());

    const createBtns = await screen.findAllByRole('button', { name: /Crear torneo/i });
    fireEvent.click(createBtns[0]);
    await waitFor(() => {
      expect(screen.getByTestId('create-modal-open')).toBeInTheDocument();
    });
  });

  it('crea un torneo y lo selecciona al enviar el modal', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    createTournamentMock.mockResolvedValue({ id: 99, name: 'Nuevo Torneo' });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getAllByRole('button', { name: /Crear torneo/i })[0]);
    fireEvent.click(await screen.findByText('submit-create'));

    await waitFor(() => {
      expect(createTournamentMock).toHaveBeenCalledWith({ name: 'Nuevo Torneo', format: 'SWISS' });
    });
    // onSuccess cierra el modal.
    await waitFor(() => {
      expect(screen.queryByTestId('create-modal-open')).not.toBeInTheDocument();
    });
  });

  it('muestra el error en el modal cuando la creación falla', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    createTournamentMock.mockRejectedValue({ response: { data: { message: 'Nombre duplicado' } } });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getAllByRole('button', { name: /Crear torneo/i })[0]);
    fireEvent.click(await screen.findByText('submit-create'));

    await waitFor(() => {
      expect(screen.getByTestId('modal-error')).toHaveTextContent('Nombre duplicado');
    });
  });

  // ── 3.1 Listado y KPIs ──────────────────────────────────────────────────
  it('renderiza las tarjetas de torneos y los KPIs cuando la lista trae datos', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    const { container } = renderPage();

    await screen.findByRole('button', { name: /Open Verano/ });
    // Los 4 nombres aparecen en la lista.
    expect(screen.getByRole('button', { name: /Clausura Primavera/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Copa Invierno/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Liga Otono/ })).toBeInTheDocument();

    // KPIs: Total / Open / En curso / Finalizados (en ese orden).
    const kpis = container.querySelectorAll('.metric-inline-value');
    expect(kpis).toHaveLength(4);
    expect(kpis[0].textContent).toBe('4'); // total
    expect(kpis[1].textContent).toBe('1'); // open
    expect(kpis[2].textContent).toBe('1'); // en curso
    expect(kpis[3].textContent).toBe('1'); // finalizados
  });

  it('selecciona automáticamente el primer torneo', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    // El header de la Card de detalle muestra el nombre del primer torneo.
    await waitFor(() => {
      expect(screen.getAllByText('Open Verano').length).toBeGreaterThan(1);
    });
    // Y su detalle (formato del primer torneo) está visible.
    expect(screen.getByText('SWISS')).toBeInTheDocument();
  });

  // ── 3.2 Búsqueda y filtros ──────────────────────────────────────────────
  it('filtra la lista al escribir en el buscador', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.change(screen.getByLabelText('Buscar torneos'), {
      target: { value: 'Clausura' },
    });

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /Copa Invierno/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Clausura Primavera/ })).toBeInTheDocument();
  });

  it('filtra por estado al clickear un chip', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getByRole('button', { name: /Borrador \(1\)/ }));

    await waitFor(() => {
      // Solo el DRAFT (Open Verano) queda en la lista.
      expect(screen.queryByRole('button', { name: /Clausura Primavera/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Open Verano/ })).toBeInTheDocument();
  });

  it('muestra EmptyState de filtro cuando ningún torneo matchea', async () => {
    // Solo torneos DRAFT; al filtrar por "En curso" no queda ninguno.
    listTournamentsMock.mockResolvedValue({
      content: [t({ id: 1, name: 'Open Verano', status: 'DRAFT' })],
    });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getByRole('button', { name: /En curso \(0\)/ }));

    await waitFor(() => {
      expect(screen.getByText('Sin torneos para este filtro')).toBeInTheDocument();
    });
  });

  // ── 3.3 Selección y detalle ─────────────────────────────────────────────
  it('al seleccionar otro torneo, muestra su detalle', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await selectCard(/Copa Invierno/);

    // El detalle pasa a mostrar el formato del torneo IN_PROGRESS (KNOCKOUT).
    await waitFor(() => {
      expect(screen.getByText('KNOCKOUT')).toBeInTheDocument();
    });
  });

  // ── 3.4 Transiciones de estado ──────────────────────────────────────────
  it('DRAFT → "Abrir inscripciones" llama patchStatus con OPEN', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    // El primer torneo (DRAFT) se autoselecciona.
    const btn = await screen.findByRole('button', { name: /Abrir inscripciones/ });
    fireEvent.click(btn);
    await waitFor(() => {
      expect(patchStatusMock).toHaveBeenCalledWith(1, 'OPEN');
    });
  });

  it('OPEN → "Iniciar torneo" llama patchStatus con IN_PROGRESS', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await selectCard(/Clausura Primavera/);
    const btn = await screen.findByRole('button', { name: /Iniciar torneo/ });
    fireEvent.click(btn);
    await waitFor(() => {
      expect(patchStatusMock).toHaveBeenCalledWith(2, 'IN_PROGRESS');
    });
  });

  it('IN_PROGRESS → "Finalizar torneo" llama patchStatus con FINISHED', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await selectCard(/Copa Invierno/);
    const btn = await screen.findByRole('button', { name: /Finalizar torneo/ });
    fireEvent.click(btn);
    await waitFor(() => {
      expect(patchStatusMock).toHaveBeenCalledWith(3, 'FINISHED');
    });
  });

  it('muestra ErrorAlert si patchStatus falla', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    patchStatusMock.mockRejectedValue(new Error('boom'));
    renderPage();
    const btn = await screen.findByRole('button', { name: /Abrir inscripciones/ });
    fireEvent.click(btn);
    await waitFor(() => {
      expect(screen.getByText('No se pudo cambiar el estado')).toBeInTheDocument();
    });
  });

  // ── 3.5 Borrado ─────────────────────────────────────────────────────────
  it('borra un torneo tras confirmar', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    // El DRAFT (Open Verano) es borrable → su botón eliminar es el primero.
    fireEvent.click(screen.getAllByLabelText('Eliminar torneo')[0]);
    await waitFor(() => {
      expect(deleteTournamentMock).toHaveBeenCalledWith(1);
    });
    confirmSpy.mockRestore();
  });

  it('no borra si el usuario cancela el confirm', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getAllByLabelText('Eliminar torneo')[0]);
    expect(deleteTournamentMock).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });

  it('muestra ErrorAlert cuando el borrado falla', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    deleteTournamentMock.mockRejectedValue(new Error('no se puede'));
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });

    fireEvent.click(screen.getAllByLabelText('Eliminar torneo')[0]);
    await waitFor(() => {
      expect(screen.getByText('No se pudo eliminar el torneo')).toBeInTheDocument();
    });
    confirmSpy.mockRestore();
  });

  // ── 3.6 Generar ronda ───────────────────────────────────────────────────
  it('genera la ronda al clickear el botón (torneo IN_PROGRESS)', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await selectCard(/Copa Invierno/);

    const btn = await screen.findByRole('button', { name: /Generar emparejamientos/ });
    expect(btn).not.toBeDisabled();
    fireEvent.click(btn);
    await waitFor(() => {
      expect(generateRoundMock).toHaveBeenCalledWith(3, 1);
    });
  });

  it('deshabilita "Generar emparejamientos" en estado DRAFT', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    // Primer torneo (DRAFT) autoseleccionado.
    const btn = await screen.findByRole('button', { name: /Generar emparejamientos/ });
    expect(btn).toBeDisabled();
  });

  it('muestra ErrorAlert si generateRound falla', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    generateRoundMock.mockRejectedValue(new Error('sin jugadores'));
    renderPage();
    await selectCard(/Copa Invierno/);

    fireEvent.click(await screen.findByRole('button', { name: /Generar emparejamientos/ }));
    await waitFor(() => {
      expect(screen.getByText('No se pudo generar la ronda')).toBeInTheDocument();
    });
  });

  // ── 3.7 Standings y emparejamientos ─────────────────────────────────────
  it('muestra StandingsTable cuando hay clasificación', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    standingsMock.mockResolvedValue([
      { rank: 1, playerId: 10, playerName: 'Ana', points: 3 },
      { rank: 2, playerId: 11, playerName: 'Beto', points: 2 },
    ]);
    renderPage();

    await waitFor(() => {
      const table = screen.getByTestId('standings-table');
      expect(table).toHaveAttribute('data-rows', '2');
    });
  });

  it('muestra ErrorAlert de clasificación y permite reintentar', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    standingsMock.mockRejectedValue(new Error('standings down'));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('No se pudo cargar la clasificación')).toBeInTheDocument();
    });
    // El onRetry del ErrorAlert de standings dispara refetch.
    fireEvent.click(screen.getByText('Reintentar'));
    await waitFor(() => {
      expect(standingsMock.mock.calls.length).toBeGreaterThan(1);
    });
  });

  it('muestra la tabla de pairings cuando la ronda trae datos', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    roundMock.mockResolvedValue({
      pairings: [
        { id: 1, boardNumber: 1, whitePlayerName: 'Ana', blackPlayerName: 'Beto', result: null },
        { id: 2, boardNumber: 2, whitePlayerName: 'Cira', blackPlayerName: 'Dino', result: '1-0' },
      ],
    });
    renderPage();

    await waitFor(() => {
      const table = screen.getByTestId('table');
      expect(table).toHaveAttribute('data-rows', '2');
    });
  });

  it('guarda el resultado de un pairing y permite cambiar la ronda', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    roundMock.mockResolvedValue({
      pairings: [{ id: 7, boardNumber: 1, whitePlayerName: 'Ana', blackPlayerName: 'Beto', result: null }],
    });
    renderPage();

    // Espera a que la tabla de pairings renderice sus celdas.
    await waitFor(() => {
      expect(screen.getByTestId('table')).toHaveAttribute('data-rows', '1');
    });

    // Comboboxes: [0] = Select "Ronda", [1] = Select de resultado del pairing.
    const combos = screen.getAllByRole('combobox');
    fireEvent.change(combos[1], { target: { value: '1-0' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await waitFor(() => {
      expect(patchPairingResultMock).toHaveBeenCalledWith(7, '1-0');
    });

    // Cambia la ronda (Select "Ronda") → dispara la query de la ronda 2 del
    // torneo autoseleccionado (id 1).
    fireEvent.change(screen.getByLabelText('Ronda'), { target: { value: '2' } });
    await waitFor(() => {
      expect(roundMock).toHaveBeenCalledWith(1, 2);
    });
  });

  it('muestra EmptyState "Sin pairings" cuando la ronda viene vacía', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    roundMock.mockResolvedValue({ pairings: [] });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText('Sin pairings')).toBeInTheDocument();
    });
  });

  it('muestra EmptyState "la ronda aún no existe" cuando round falla', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    roundMock.mockRejectedValue(new Error('404'));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText(/La ronda 1 aún no existe/)).toBeInTheDocument();
    });
  });

  // ── Refresh ─────────────────────────────────────────────────────────────
  it('al clickear "Actualizar lista" invalida y refetchea la lista', async () => {
    listTournamentsMock.mockResolvedValue({ content: FOUR });
    renderPage();
    await screen.findByRole('button', { name: /Open Verano/ });
    const callsBefore = listTournamentsMock.mock.calls.length;

    fireEvent.click(screen.getByLabelText('Actualizar lista'));
    await waitFor(() => {
      expect(listTournamentsMock.mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });
});
