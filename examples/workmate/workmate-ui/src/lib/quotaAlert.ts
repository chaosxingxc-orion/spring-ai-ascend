import type { TenantQuota } from '../api/tenant';

export function exceededQuotaMessages(quota: TenantQuota | null | undefined): string[] {
  return quota?.alerts?.filter((alert) => alert.level === 'exceeded').map((alert) => alert.message) ?? [];
}

export function formatQuotaExceededToast(
  quota: TenantQuota | null | undefined,
  fallback: string,
): string {
  const messages = exceededQuotaMessages(quota);
  return messages.length > 0 ? messages.join('；') : fallback;
}

export function isQuotaOrSessionLimitError(message: string): boolean {
  return /quota|limit exceeded|maxActive|archivable|Session limit/i.test(message);
}
