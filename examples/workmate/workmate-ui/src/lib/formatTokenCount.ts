import { formatCompactNumber } from './formatLocale';

/** Compact token count for sidebar / badge (locale-aware compact notation). */
export function formatTokenCount(value: number): string {
  if (!Number.isFinite(value) || value <= 0) {
    return '0';
  }
  if (value < 1000) {
    return String(Math.round(value));
  }
  return formatCompactNumber(value);
}
