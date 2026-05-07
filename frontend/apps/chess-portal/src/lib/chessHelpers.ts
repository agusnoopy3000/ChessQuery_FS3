// Helpers de presentación para partidas live: derivan flag emoji desde el
// código ISO del país y calculan piezas capturadas + delta material a partir
// del FEN. Sin dependencias del backend — son funciones puras.

const PIECE_VALUES: Record<string, number> = {
  p: 1, n: 3, b: 3, r: 5, q: 9, k: 0,
};

// Cantidades iniciales de piezas por color (orden estándar).
const INITIAL_COUNTS: Record<string, number> = {
  p: 8, n: 2, b: 2, r: 2, q: 1, k: 1,
};

export type CapturedPiece = { piece: string; symbol: string };

const SYMBOL_FOR: Record<string, string> = {
  P: '♙', N: '♘', B: '♗', R: '♖', Q: '♕',
  p: '♟', n: '♞', b: '♝', r: '♜', q: '♛',
};

/**
 * Convierte un código ISO 3166-1 alpha-2 (ej: "CL", "AR") en el emoji
 * de bandera correspondiente usando regional indicator symbols.
 * Devuelve null si el código no parece válido.
 */
export const flagFromIsoCode = (iso: string | null | undefined): string | null => {
  if (!iso) return null;
  const code = iso.trim().toUpperCase();
  if (code.length !== 2 || !/^[A-Z]{2}$/.test(code)) return null;
  const A = 0x1f1e6;
  const a = 'A'.charCodeAt(0);
  return String.fromCodePoint(A + code.charCodeAt(0) - a, A + code.charCodeAt(1) - a);
};

interface MaterialBalance {
  capturedByWhite: CapturedPiece[]; // piezas negras que blancas capturaron
  capturedByBlack: CapturedPiece[];
  delta: number;                     // positivo = ventaja de blancas
}

/**
 * Calcula material capturado y delta a partir de un FEN.
 * Compara la posición actual con la inicial; lo que falta = capturado.
 */
export const computeMaterialBalance = (fen: string): MaterialBalance => {
  const board = fen.split(' ')[0] ?? '';
  const counts: Record<string, number> = {
    P: 0, N: 0, B: 0, R: 0, Q: 0, K: 0,
    p: 0, n: 0, b: 0, r: 0, q: 0, k: 0,
  };
  for (const ch of board) {
    if (ch in counts) counts[ch] += 1;
  }
  const capturedByWhite: CapturedPiece[] = [];
  const capturedByBlack: CapturedPiece[] = [];
  let valueWhite = 0;
  let valueBlack = 0;
  for (const piece of ['p', 'n', 'b', 'r', 'q'] as const) {
    const lostBlack = (INITIAL_COUNTS[piece] ?? 0) - (counts[piece] ?? 0);
    const lostWhite = (INITIAL_COUNTS[piece] ?? 0) - (counts[piece.toUpperCase()] ?? 0);
    for (let i = 0; i < Math.max(0, lostBlack); i += 1) {
      capturedByWhite.push({ piece, symbol: SYMBOL_FOR[piece] });
      valueWhite += PIECE_VALUES[piece];
    }
    for (let i = 0; i < Math.max(0, lostWhite); i += 1) {
      capturedByBlack.push({ piece: piece.toUpperCase(), symbol: SYMBOL_FOR[piece.toUpperCase()] });
      valueBlack += PIECE_VALUES[piece];
    }
  }
  return {
    capturedByWhite,
    capturedByBlack,
    delta: valueWhite - valueBlack,
  };
};
