/**
 * URL del portal del jugador (chess-portal, otra app en otro origen).
 *
 * En producción se define `VITE_PORTAL_URL` (p.ej. la URL del bucket S3 del
 * chess-portal). En dev local cae al puerto 5173 de Vite.
 */
export const playerPortalUrl = (): string =>
  import.meta.env.VITE_PORTAL_URL || 'http://localhost:5173';
