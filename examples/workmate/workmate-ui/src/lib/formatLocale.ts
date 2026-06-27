import { useSettings } from '../features/settings/SettingsProvider';
import { getI18nLanguage } from './i18n';
import { t } from './i18n';

function localeTag(): string {
  return getI18nLanguage() === 'en' ? 'en-US' : 'zh-CN';
}

export function formatNumber(value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(localeTag(), options).format(value);
}

export function formatCompactNumber(value: number): string {
  return new Intl.NumberFormat(localeTag(), {
    notation: 'compact',
    maximumFractionDigits: value < 10_000 ? 1 : 0,
  }).format(value);
}

export function formatDateTime(value: string | number | Date): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat(localeTag(), {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatDateLong(value: string | number | Date = Date.now()): string {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return new Intl.DateTimeFormat(localeTag(), {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '0 B';
  }
  if (bytes < 1024) {
    return `${formatNumber(bytes)} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${formatNumber(bytes / 1024, { maximumFractionDigits: 1 })} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${formatNumber(bytes / (1024 * 1024), { maximumFractionDigits: 1 })} MB`;
  }
  return `${formatNumber(bytes / (1024 * 1024 * 1024), { maximumFractionDigits: 1 })} GB`;
}

export function formatRelativeTime(from: string | number, nowMs = Date.now()): string {
  const fromMs = typeof from === 'number' ? from : Date.parse(from);
  if (!Number.isFinite(fromMs)) {
    return t('time.justNow');
  }
  const deltaSec = Math.max(0, Math.floor((nowMs - fromMs) / 1000));
  if (deltaSec < 10) {
    return t('time.justNow');
  }
  if (deltaSec < 60) {
    return t('time.secondsAgo', { n: deltaSec });
  }
  const deltaMin = Math.floor(deltaSec / 60);
  if (deltaMin < 60) {
    return t('time.minutesAgo', { n: deltaMin });
  }
  const deltaHour = Math.floor(deltaMin / 60);
  if (deltaHour < 24) {
    return t('time.hoursAgo', { n: deltaHour });
  }
  const deltaDay = Math.floor(deltaHour / 24);
  return t('time.daysAgo', { n: deltaDay });
}

/** Subscribe to settings language so formatted labels re-render on locale change. */
export function useLocaleKey(): string {
  return useSettings().settings.language;
}
