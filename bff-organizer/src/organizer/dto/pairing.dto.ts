export interface PairingResponse {
  id: number;
  roundId: number;
  boardNumber: number;
  whitePlayerId: number | null;
  blackPlayerId: number | null;
  result: string | null;
}

export interface RoundResponse {
  id: number;
  tournamentId: number;
  roundNumber: number;
  status: string;
  pairings: PairingResponse[];
}

export interface EnrichedPairing extends PairingResponse {
  whitePlayerName?: string;
  whitePlayerRating?: number;
  blackPlayerName?: string;
  blackPlayerRating?: number;
}

export interface EnrichedRoundResponse extends Omit<RoundResponse, 'pairings'> {
  pairings: EnrichedPairing[];
}

export interface PlayerProfile {
  id: number;
  firstName?: string;
  lastName?: string;
  eloNational?: number;
  eloFideStandard?: number;
}
