export interface DashboardResponse {
  profile: unknown;
  recentGames: unknown[];
  stats: unknown;
}

export interface RatingChartPoint {
  date: string;
  rating: number;
}

export interface RatingHistoryEntry {
  recordedAt: string;
  value: number;
  ratingType: string;
}
