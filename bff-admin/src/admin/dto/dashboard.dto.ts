export interface AdminDashboardResponse {
  users: { total: number | null; error?: string };
  tournaments: { active: unknown | null; error?: string };
  games: { recent: unknown | null; error?: string };
  analytics: { platform: unknown | null; error?: string };
  etl: { status: unknown | null; error?: string };
}
