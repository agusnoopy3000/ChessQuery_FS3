/**
 * Copia texto al portapapeles de forma robusta.
 *
 * `navigator.clipboard` sólo existe en contextos seguros (HTTPS o localhost).
 * En producción servimos por HTTP (S3 website) → ahí `navigator.clipboard` es
 * undefined y el copiado fallaba en silencio. Por eso caemos a un fallback con
 * un <textarea> temporal + `document.execCommand('copy')`, que funciona en HTTP.
 *
 * @returns true si se logró copiar.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  // Camino moderno (sólo en contexto seguro).
  if (typeof navigator !== 'undefined' && navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      /* cae al fallback */
    }
  }
  // Fallback HTTP: textarea fuera de pantalla + execCommand.
  try {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.style.position = 'fixed';
    ta.style.top = '-1000px';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    ta.setSelectionRange(0, text.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    return ok;
  } catch {
    return false;
  }
}
