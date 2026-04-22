import { Table, TableColumn } from './Table';

export interface StandingEntry {
  rank: number;
  playerId: number | string;
  playerName: string;
  points: number;
  buchholz?: number;
  sonneborn?: number;
  gamesPlayed?: number;
}

export interface StandingsTableProps {
  entries: StandingEntry[];
  showTiebreakers?: boolean;
}

export const StandingsTable = ({ entries, showTiebreakers = true }: StandingsTableProps) => {
  const columns: TableColumn<StandingEntry>[] = [
    {
      key: 'rank',
      header: '#',
      width: 44,
      render: (r) => (
        <span
          style={{
            fontFamily: "'Space Grotesk', system-ui, sans-serif",
            fontWeight: 700,
            color: r.rank <= 3 ? 'var(--accent)' : 'var(--text-muted)',
          }}
        >
          {r.rank}
        </span>
      ),
    },
    {
      key: 'name',
      header: 'Jugador',
      render: (r) => (
        <span style={{ fontFamily: "'Space Grotesk', system-ui, sans-serif", fontWeight: 600 }}>
          {r.playerName}
        </span>
      ),
    },
    {
      key: 'points',
      header: 'Puntos',
      align: 'right',
      render: (r) => (
        <span
          style={{
            fontFamily: "'Space Grotesk', system-ui, sans-serif",
            fontWeight: 700,
            color: 'var(--accent)',
          }}
        >
          {r.points.toFixed(1)}
        </span>
      ),
    },
  ];

  if (showTiebreakers) {
    columns.push({
      key: 'buchholz',
      header: 'Buchholz',
      align: 'right',
      render: (r) => (
        <span style={{ color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 12 }}>
          {r.buchholz != null ? r.buchholz.toFixed(1) : '—'}
        </span>
      ),
    });
    columns.push({
      key: 'sonneborn',
      header: 'Son.-Berger',
      align: 'right',
      render: (r) => (
        <span style={{ color: 'var(--text-muted)', fontFamily: 'monospace', fontSize: 12 }}>
          {r.sonneborn != null ? r.sonneborn.toFixed(1) : '—'}
        </span>
      ),
    });
  }

  return (
    <Table
      columns={columns}
      rows={entries}
      rowKey={(e) => e.playerId}
      emptyMessage="Aún no hay posiciones registradas"
    />
  );
};
