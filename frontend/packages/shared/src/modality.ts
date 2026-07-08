/**
 * Modalidades de tiempo (EP-P1 / P1-01).
 *
 * Fuente única de verdad de los presets de reloj y de la clasificación de una
 * partida en su familia de modalidad (bullet / blitz / rapid / clásica).
 *
 * El backend ya persiste `timeControlInitialMs` / `timeControlIncrementMs` en la
 * sesión live y escribe el tag `[TimeControl "base+inc"]` en el PGN; este módulo
 * es el lado front que traduce presets ↔ milisegundos y deriva la familia.
 *
 * `modalityFamily()` usa el mismo umbral estilo Lichess/FIDE que se aplicará en
 * el backend cuando se active el ELO segmentado por modalidad (ver ADR-0001):
 * duración estimada = segundos iniciales + 40 × incremento.
 */

export type ModalityFamily = 'bullet' | 'blitz' | 'rapid' | 'classical';

export interface TimeControl {
  initialMs: number;
  incrementMs: number;
}

export interface TimeControlPreset extends TimeControl {
  /** id estable, p. ej. 'blitz-3-0' */
  id: string;
  /** etiqueta corta 'min+inc', p. ej. '3+0' */
  label: string;
  family: ModalityFamily;
}

export const MODALITY_LABELS: Record<ModalityFamily, string> = {
  bullet: 'Bullet',
  blitz: 'Blitz',
  rapid: 'Rapid',
  classical: 'Clásica',
};

export const MODALITY_ORDER: ModalityFamily[] = ['bullet', 'blitz', 'rapid', 'classical'];

const min = (m: number) => m * 60_000;
const sec = (s: number) => s * 1_000;

/**
 * Deriva la familia de modalidad a partir del control de tiempo.
 * Umbral sobre duración estimada = inicial(s) + 40 × incremento(s):
 *   < 179s → bullet · < 479s → blitz · < 1499s → rapid · resto → clásica.
 */
export function modalityFamily(initialMs: number, incrementMs: number): ModalityFamily {
  const estimatedSeconds = initialMs / 1000 + 40 * (incrementMs / 1000);
  if (estimatedSeconds < 179) return 'bullet';
  if (estimatedSeconds < 479) return 'blitz';
  if (estimatedSeconds < 1499) return 'rapid';
  return 'classical';
}

/** Formatea 'min+incSeg', p. ej. 180000/2000 → '3+2'. */
export function formatTimeControl(initialMs: number, incrementMs: number): string {
  return `${Math.round(initialMs / 60_000)}+${Math.round(incrementMs / 1000)}`;
}

function preset(initialMs: number, incrementMs: number): TimeControlPreset {
  const family = modalityFamily(initialMs, incrementMs);
  const label = formatTimeControl(initialMs, incrementMs);
  return { id: `${family}-${label.replace('+', '-')}`, label, family, initialMs, incrementMs };
}

/** Presets estándar por familia (P1-01). */
export const TIME_CONTROL_PRESETS: TimeControlPreset[] = [
  // Bullet
  preset(min(1), 0),
  preset(min(2), sec(1)),
  // Blitz
  preset(min(3), 0),
  preset(min(3), sec(2)),
  preset(min(5), 0),
  preset(min(5), sec(3)),
  // Rapid
  preset(min(10), 0),
  preset(min(10), sec(5)),
  preset(min(15), sec(10)),
  // Clásica
  preset(min(30), 0),
  preset(min(30), sec(20)),
];

/** Preset por defecto (Blitz 3+0), coincide con el comportamiento previo hardcodeado. */
export const DEFAULT_PRESET_ID = 'blitz-3-0';

export function presetById(id: string): TimeControlPreset | undefined {
  return TIME_CONTROL_PRESETS.find((p) => p.id === id);
}

export function presetsByFamily(family: ModalityFamily): TimeControlPreset[] {
  return TIME_CONTROL_PRESETS.filter((p) => p.family === family);
}
