/**
 * URL del panel del organizador (es una app aparte, en otro origen).
 *
 * En producción se define `VITE_ORGANIZER_URL` (p.ej. la URL del bucket S3 del
 * organizer-panel). En dev local cae al puerto 5174 de Vite sobre el mismo host.
 *
 * Antes esto estaba hardcodeado a `:5174` en varios lugares, lo que rompía la
 * redirección de organizadores en producción (host S3 sin ese puerto).
 */
export const organizerPanelUrl = (): string =>
  import.meta.env.VITE_ORGANIZER_URL ||
  `${window.location.protocol}//${window.location.hostname}:5174`;
