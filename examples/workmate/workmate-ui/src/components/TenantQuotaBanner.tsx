import type { TenantQuota } from '../api/tenant';

interface TenantQuotaBannerProps {
  quota: TenantQuota | null;
  onOpenQuota: () => void;
  /** When auto-archive is on, active-session cap alerts are expected and need no top banner. */
  suppressActiveSessionAlerts?: boolean;
}

/** W45 — enterprise quota alerts (e.g. activeSessions exceeded). */
export function TenantQuotaBanner({
  quota,
  onOpenQuota,
  suppressActiveSessionAlerts = false,
}: TenantQuotaBannerProps) {
  if (!quota?.alerts?.length) {
    return null;
  }
  let exceeded = quota.alerts.filter((alert) => alert.level === 'exceeded');
  if (suppressActiveSessionAlerts) {
    exceeded = exceeded.filter((alert) => alert.metric !== 'activeSessions');
  }
  if (exceeded.length === 0) {
    return null;
  }

  return (
    <div className="tenant-quota-banner" role="alert">
      <p className="tenant-quota-banner-text">
        {exceeded.map((alert) => alert.message).join('；')}
      </p>
      <button type="button" className="tenant-quota-banner-action" onClick={onOpenQuota}>
        查看配额
      </button>
    </div>
  );
}
