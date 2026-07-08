import { describe, it, expect } from 'vitest';
import {
  DEFAULT_PRESET_ID,
  TIME_CONTROL_PRESETS,
  formatTimeControl,
  modalityFamily,
  presetById,
  presetsByFamily,
} from '@chessquery/shared';

describe('modalityFamily', () => {
  it('clasifica bullet (1+0, 2+1)', () => {
    expect(modalityFamily(60_000, 0)).toBe('bullet');
    expect(modalityFamily(120_000, 1_000)).toBe('bullet');
  });

  it('clasifica blitz (3+0, 5+3)', () => {
    expect(modalityFamily(180_000, 0)).toBe('blitz');
    expect(modalityFamily(300_000, 3_000)).toBe('blitz');
  });

  it('clasifica rapid (10+0, 15+10)', () => {
    expect(modalityFamily(600_000, 0)).toBe('rapid');
    expect(modalityFamily(900_000, 10_000)).toBe('rapid');
  });

  it('clasifica clásica (30+0, 30+20)', () => {
    expect(modalityFamily(1_800_000, 0)).toBe('classical');
    expect(modalityFamily(1_800_000, 20_000)).toBe('classical');
  });
});

describe('formatTimeControl', () => {
  it('formatea min+incSeg', () => {
    expect(formatTimeControl(180_000, 2_000)).toBe('3+2');
    expect(formatTimeControl(60_000, 0)).toBe('1+0');
  });
});

describe('presets', () => {
  it('cada preset se autoclasifica en la familia de su id', () => {
    for (const p of TIME_CONTROL_PRESETS) {
      expect(p.family).toBe(modalityFamily(p.initialMs, p.incrementMs));
      expect(p.id.startsWith(p.family)).toBe(true);
    }
  });

  it('el preset por defecto existe y es Blitz 3+0', () => {
    const def = presetById(DEFAULT_PRESET_ID);
    expect(def).toBeDefined();
    expect(def?.family).toBe('blitz');
    expect(def?.label).toBe('3+0');
  });

  it('presetsByFamily agrupa correctamente', () => {
    expect(presetsByFamily('bullet').map((p) => p.label)).toEqual(['1+0', '2+1']);
    expect(presetsByFamily('classical').map((p) => p.label)).toEqual(['30+0', '30+20']);
  });

  it('presetById devuelve undefined para id inexistente', () => {
    expect(presetById('no-existe')).toBeUndefined();
  });
});
