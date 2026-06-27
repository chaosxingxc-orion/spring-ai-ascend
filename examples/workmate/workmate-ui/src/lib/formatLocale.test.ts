import { describe, expect, it } from 'vitest';
import { setI18nLanguage } from './i18n';
import {
  formatCompactNumber,
  formatDateLong,
  formatFileSize,
  formatNumber,
  formatRelativeTime,
} from './formatLocale';

describe('formatLocale', () => {
  it('formats numbers with locale grouping', () => {
    setI18nLanguage('en');
    expect(formatNumber(1234567)).toMatch(/1,234,567|1\.234\.567/);
    setI18nLanguage('zh');
  });

  it('formats relative time in English', () => {
    setI18nLanguage('en');
    const now = Date.now();
    expect(formatRelativeTime(now - 30_000, now)).toBe('30s ago');
    setI18nLanguage('zh');
  });

  it('formats relative time in Chinese', () => {
    setI18nLanguage('zh');
    const now = Date.now();
    expect(formatRelativeTime(now - 120_000, now)).toBe('2 分钟前');
  });

  it('formats file sizes with locale-aware numbers', () => {
    setI18nLanguage('en');
    expect(formatFileSize(512)).toBe('512 B');
    expect(formatFileSize(1536)).toContain('KB');
    setI18nLanguage('zh');
    expect(formatFileSize(2048)).toContain('KB');
  });

  it('formats long dates per locale', () => {
    setI18nLanguage('en');
    const en = formatDateLong('2026-06-20T12:00:00Z');
    expect(en.length).toBeGreaterThan(5);
    setI18nLanguage('zh');
    const zh = formatDateLong('2026-06-20T12:00:00Z');
    expect(zh.length).toBeGreaterThan(5);
    expect(zh).not.toBe(en);
  });

  it('formats compact numbers per locale', () => {
    setI18nLanguage('en');
    expect(formatCompactNumber(1200)).toMatch(/1\.2K/i);
    setI18nLanguage('zh');
    expect(formatCompactNumber(1200)).toMatch(/1\.2|1200/);
  });
});
