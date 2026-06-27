import type { ToolStatus } from '../../types/events';
import { isToolFailed, isToolInProgress, statusLabel as toolStatusLabel } from '../../lib/toolStatus';

export function formatJson(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

/** @deprecated Prefer {@link formatJson} inside expanded detail views. */
export function truncateJson(value: unknown, max = 2000): string {
  const text = formatJson(value);
  if (text.length <= max) {
    return text;
  }
  return `${text.slice(0, max)}\n… (truncated)`;
}

export { toolStatusLabel as statusLabel, isToolInProgress, isToolFailed };

export function isToolActive(status: ToolStatus): boolean {
  return isToolInProgress(status);
}
