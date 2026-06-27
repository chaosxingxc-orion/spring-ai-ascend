import { test, expect } from '@playwright/test';
import { stubCommonRoutes, stubSessionRoutes, stubV10EnterpriseRoutes } from '../fixtures/stubs';

test.describe('v1.0 enterprise pages (W40–W43 E2E)', () => {
  test.beforeEach(async ({ page }) => {
    await stubCommonRoutes(page);
    await stubV10EnterpriseRoutes(page);
  });

  test('automation hub shows cron jobs and cloud sessions', async ({ page }) => {
    await page.goto('/automation');
    await expect(page.locator('.app-shell-audit')).toBeVisible();
    await expect(page.getByRole('heading', { name: '自动化' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '定时任务' })).toBeVisible();
    await expect(page.getByRole('button', { name: '新建定时任务' })).toBeVisible();
    await expect(page.getByText('每日研报')).toBeVisible();
    await expect(page.getByRole('heading', { name: /云 Session/ })).toBeVisible();
    await expect(page.getByText('云基金研究')).toBeVisible();
    await expect(page.getByText('runtime reachable')).toBeVisible();
    await expect(page.getByRole('heading', { name: /IM Webhook/ })).toBeVisible();
    await expect(page.getByText('/api/v1/automation/webhooks/generic')).toBeVisible();
    await expect(page.getByRole('heading', { name: '最近触发' })).toBeVisible();
    await expect(page.getByText('SKIPPED')).toBeVisible();
    await expect(page.locator('.sidebar-nav-item.active .sidebar-nav-label')).toHaveText('自动化');
  });

  test('settings quota page shows tenant metrics', async ({ page }) => {
    await page.goto('/settings/quota');
    await expect(page.locator('.settings-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: '企业配额' })).toBeVisible();
    await expect(page.getByText('活跃会话')).toBeVisible();
    await expect(page.getByText('本月 Token')).toBeVisible();
    await expect(page.getByRole('link', { name: '企业配额' })).toHaveClass(/active/);
  });

  test('linked session shows cloud badge (W43.1)', async ({ page }) => {
    const linkedSession = {
      id: 'sess-linked-1',
      title: '云基金研究 · 云',
      status: 'CREATED',
      expertId: 'fund-analyst',
      permissionMode: 'CRAFT',
      workspacePath: '/tmp/ws',
      promptTokens: 0,
      completionTokens: 0,
    };
    await stubCommonRoutes(page, { sessions: [linkedSession] });
    await stubV10EnterpriseRoutes(page);
    await stubSessionRoutes(page, 'sess-linked-1', { session: linkedSession, messages: [] });
    await page.goto('/s/sess-linked-1');
    await expect(page.getByText('云 · RUNNING')).toBeVisible();
  });

  test('quota exceeded banner shows on home (W45)', async ({ page }) => {
    await stubCommonRoutes(page);
    await stubV10EnterpriseRoutes(page);
    await page.route('**/api/v1/tenant/quota', async (route) => {
      await route.fulfill({
        json: {
          tenantId: 'default',
          period: '2026-06',
          metrics: [
            { key: 'activeSessions', label: '活跃会话', used: 50, limit: 50, percentUsed: 100, status: 'exceeded' },
          ],
          alerts: [
            { key: 'activeSessions', level: 'exceeded', message: '活跃会话已达上限（50/50）' },
          ],
        },
      });
    });
    await page.goto('/');
    await expect(page.locator('.tenant-quota-banner')).toBeVisible();
    await expect(page.getByText('活跃会话已达上限')).toBeVisible();
    await expect(page.getByRole('button', { name: '查看配额' })).toBeVisible();
  });

  test('cron wizard opens and advances to task step (W48-D1)', async ({ page }) => {
    await page.goto('/automation');
    await page.getByRole('button', { name: '新建定时任务' }).click();
    await expect(page.getByText('执行计划')).toBeVisible();
    await expect(page.getByRole('button', { name: '工作日' })).toBeVisible();
    await page.getByRole('button', { name: '下一步' }).click();
    await expect(page.getByPlaceholder('例如：每日研报')).toBeVisible();
    await expect(page.getByRole('button', { name: '上一步' })).toBeVisible();
  });
});
