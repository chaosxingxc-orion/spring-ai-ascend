import { useCallback, useEffect, useState } from 'react';
import { formatBytes } from '../../api/data';
import { getTenantQuota, type TenantQuota } from '../../api/tenant';

function formatMetricValue(key: string, used: number): string {
  if (key === 'storageBytes') {
    return formatBytes(used);
  }
  if (key === 'monthlyTokens') {
    return used.toLocaleString();
  }
  return String(used);
}

function formatMetricLimit(key: string, limit: number): string {
  if (limit <= 0) {
    return '不限';
  }
  if (key === 'storageBytes') {
    return formatBytes(limit);
  }
  if (key === 'monthlyTokens') {
    return limit.toLocaleString();
  }
  return String(limit);
}

export function TenantQuotaPanel() {
  const [quota, setQuota] = useState<TenantQuota | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setQuota(await getTenantQuota());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <div className="settings-panel tenant-quota-panel">
      <p className="settings-panel-desc">
        单租户配额用量与告警（配置项 <code>workmate.tenant.quota.*</code>）。0 表示不限。
      </p>

      {error && <p className="memory-settings-error" role="alert">{error}</p>}

      {loading ? (
        <p className="memory-settings-empty">加载中…</p>
      ) : quota ? (
        <>
          <p className="muted tenant-quota-period">
            租户 <strong>{quota.tenantId}</strong> · 账期 {quota.period}
          </p>

          {quota.alerts.length > 0 && (
            <ul className="tenant-quota-alerts">
              {quota.alerts.map((alert) => (
                <li key={`${alert.metric}-${alert.level}`} className={`quota-alert-${alert.level}`}>
                  {alert.message}
                </li>
              ))}
            </ul>
          )}

          <ul className="tenant-quota-metrics">
            {quota.metrics.map((metric) => (
              <li key={metric.key} className={`tenant-quota-metric status-${metric.status}`}>
                <div className="tenant-quota-metric-head">
                  <span>{metric.label}</span>
                  <span>
                    {formatMetricValue(metric.key, metric.used)}
                    {' / '}
                    {formatMetricLimit(metric.key, metric.limit)}
                  </span>
                </div>
                {metric.limit > 0 && (
                  <div className="tenant-quota-bar" aria-hidden>
                    <div
                      className="tenant-quota-bar-fill"
                      style={{ width: `${Math.min(100, metric.percentUsed)}%` }}
                    />
                  </div>
                )}
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
