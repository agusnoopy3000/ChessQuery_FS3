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
}

export interface PlayerCardProps {
  player: PlayerCardPlayer;
  onClick?: () => void;
  ratingLabel?: string;
}

export const PlayerCard = ({ player, onClick, ratingLabel = 'ELO' }: PlayerCardProps) => {
  const fullName = player.name ?? [player.firstName, player.lastName].filter(Boolean).join(' ') ?? '—';
  const rating = player.eloFideStandard ?? player.eloNational ?? null;

  return (
    <Card
      hover
      style={{ cursor: onClick ? 'pointer' : undefined }}
      onClick={onClick}
      padded
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <div
          style={{
            width: 44,
            height: 44,
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
          {fullName.charAt(0).toUpperCase()}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <div
              style={{
                fontFamily: "'Space Grotesk', system-ui, sans-serif",
                fontWeight: 600,
                fontSize: 14,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {fullName}
            </div>
            {player.fideTitle && <Badge variant="gold">{player.fideTitle}</Badge>}
          </div>
          <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2 }}>
            {player.countryFlag && <span style={{ marginRight: 4 }}>{player.countryFlag}</span>}
            {player.clubName ?? player.countryName ?? 'Sin club'}
          </div>
        </div>
        {rating != null && <RatingBadge rating={rating} label={ratingLabel} />}
      </div>
    </Card>
  );
};
