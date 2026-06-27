import { describe, expect, it } from 'vitest';
import { setI18nLanguage } from './i18n';
import { formatTokenCount } from './formatTokenCount';

describe('formatTokenCount', () => {
  it('formats small values', () => {
    expect(formatTokenCount(0)).toBe('0');
    expect(formatTokenCount(42)).toBe('42');
    expect(formatTokenCount(999)).toBe('999');
  });

  it('formats thousands in English', () => {
    setI18nLanguage('en');
    expect(formatTokenCount(1200)).toMatch(/1\.2K/i);
    expect(formatTokenCount(120000)).toMatch(/120K/i);
    setI18nLanguage('zh');
  });
});
