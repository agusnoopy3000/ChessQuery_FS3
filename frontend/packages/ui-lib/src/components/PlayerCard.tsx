import { Card } from './Card';
import { Badge } from './Badge';
import { RatingBadge } from './RatingBadge';

export interface PlayerCardPlayer {
  id: number | string;
  firstName?: string;
  lastName?: string;
  name?: string;
  clubName?: string | null;
  countryName?: string | null;
  countryFlag?: string | null;
  fideTitle?: string | null;
  eloFideStandard?: number | null;
  eloNational?: number | null;
  eloPlatform?: number | null;
  enrichmentSource?: string | null;
}

export interface PlayerCardProps {
  player: PlayerCardPlayer;
  onClick?: () => void;
}

export const PlayerCard = ({ player, onClick }: PlayerCardProps) => {
  const fullName = player.name ?? [player.firstName, player.lastName].filter(Boolean).join(' ') ?? '—';
  const initials = (player.firstName?.[0] ?? '?') + (player.lastName?.[0] ?? '');

  return (
    <Card
      hover
      style={{ cursor: onClick ? 'pointer' : undefined }}
      onClick={onClick}
      padded
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div
          style={{
            width: 52,
            height: 52,
            borderRadius: '50%',
            background: 'var(--accent-dim)',
            border: '2px solid var(--accent-outline)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 18,
            color: 'var(--accent)',
            fontFamily: "'Space Grotesk', system-ui, sans-serif",
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          {initials.toUpperCase()}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui, sans-serif",
                fontWeight: 600,
                fontSize: 15,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {fullName}
            </div>
            {player.fideTitle && <Badge variant="gold">{player.fideTitle}</Badge>}
            {player.enrichmentSource && <Badge>{player.enrichmentSource}</Badge>}
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 4 }}>
            {player.countryFlag && <span style={{ marginRight: 4 }}>{player.countryFlag}</span>}
            {player.clubName ?? player.countryName ?? 'Sin club'}
          </div>
        </div>

        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {player.eloNational != null && <RatingBadge rating={player.eloNational} label="NAC" />}
          {player.eloFideStandard != null && <RatingBadge rating={player.eloFideStandard} label="FIDE" />}
          {player.eloPlatform != null && <RatingBadge rating={player.eloPlatform} label="LICHESS" />}
        </div>
      </div>
    </Card>
  );
};
