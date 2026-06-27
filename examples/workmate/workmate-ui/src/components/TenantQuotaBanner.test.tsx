import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { TenantQuotaBanner } from './TenantQuotaBanner';
import type { TenantQuota } from '../api/tenant';

const exceededQuota: TenantQuota = {
  tenantId: 'default',
  period: '2026-06',
  metrics: [],
  alerts: [
    {
      metric: 'activeSessions',
      level: 'exceeded',
      message: '活跃会话已达上限（50/50）',
    },
  ],
};

describe('TenantQuotaBanner', () => {
  it('renders nothing when quota is null', () => {
    expect(
      renderToStaticMarkup(<TenantQuotaBanner quota={null} onOpenQuota={() => undefined} />),
    ).toBe('');
  });

  it('renders nothing when no exceeded alerts', () => {
    expect(
      renderToStaticMarkup(
        <TenantQuotaBanner
          quota={{ ...exceededQuota, alerts: [] }}
          onOpenQuota={() => undefined}
        />,
      ),
    ).toBe('');
  });

  it('shows exceeded alert with action', () => {
    const html = renderToStaticMarkup(
      <TenantQuotaBanner quota={exceededQuota} onOpenQuota={() => undefined} />,
    );
    expect(html).toContain('活跃会话已达上限');
    expect(html).toContain('查看配额');
    expect(html).toContain('role="alert"');
  });

  it('hides active-session exceeded when suppressed', () => {
    expect(
      renderToStaticMarkup(
        <TenantQuotaBanner
          quota={exceededQuota}
          suppressActiveSessionAlerts
          onOpenQuota={() => undefined}
        />,
      ),
    ).toBe('');
  });
});
