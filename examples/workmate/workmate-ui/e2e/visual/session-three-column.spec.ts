import { test, expect } from '@playwright/test';
import {
  expertsFixture,
  s11ChatFixture,
  s11SessionFixture,
  s11SessionId,
  s11SessionsFixture,
} from '../fixtures/api';
import { stubCommonRoutes } from '../fixtures/stubs';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(
    ({ sessionId, items }) => {
      localStorage.setItem('workmate.activeSessionId', sessionId);
      localStorage.setItem(`workmate.chat.${sessionId}`, JSON.stringify(items));
    },
    { sessionId: s11SessionId, items: s11ChatFixture.items },
  );
  await stubCommonRoutes(page, { sessions: s11SessionsFixture });
  await page.route(`**/api/v1/sessions/${s11SessionId}`, async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: s11SessionFixture });
      return;
    }
    await route.continue();
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/messages`, async (route) => {
    await route.fulfill({ json: s11ChatFixture.items });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/artifacts`, async (route) => {
    await route.fulfill({ json: s11ChatFixture.artifacts });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/workspace/entries**`, async (route) => {
    await route.fulfill({ json: s11ChatFixture.workspaceEntries });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/pending-approvals`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/pending-questions`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/run-events`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/v1/sessions/${s11SessionId}/team-snapshot`, async (route) => {
    await route.fulfill({
      json: {
        teamId: 'product-strategy-team',
        pattern: 'orchestrator',
        members: expertsFixture
          .find((e) => e.id === 'product-strategy-team')
          ?.members?.map((m) => ({
            memberId: m.id,
            name: m.name,
            role: m.role,
            status: 'idle',
          })),
      },
    });
  });
});

/** 结构验收 — 对齐 workmate-three-column-hifi.png（非截图基线） */
test.describe('S11 session three-column — workmate-three-column-hifi.png', () => {
  test.describe.configure({ mode: 'serial' });

  test('renders WorkMate session chrome per mockup', async ({ page }) => {
    await page.goto(`/s/${s11SessionId}`);
    await expect(page.getByRole('heading', { name: '分析竞品定价策略' })).toBeVisible();
    await expect(page.getByRole('button', { name: '分享' })).toBeVisible();
    await expect(page.locator('.session-header-overflow-menu .session-item-menu-trigger')).toBeVisible();
    await expect(page.locator('.chat-messages-workmate')).toBeVisible();
    await expect(page.getByText('WorkMate', { exact: true })).toBeVisible();
    await expect(page.locator('.session-team-viz-anchor')).toBeVisible();
    await expect(page.getByRole('button', { name: '产物' })).toBeVisible();
    await expect(page.getByRole('button', { name: '全部文件' })).toBeVisible();
    await expect(page.getByRole('button', { name: '变更' })).toBeVisible();
    await expect(page.locator('.detail-panel-tabs').getByRole('button', { name: '专家' })).toBeVisible();
    await expect(page.getByRole('button', { name: '概览' })).toBeVisible();
    await expect(page.getByRole('region', { name: '团队信息' })).toBeVisible();
    await expect(page.getByRole('button', { name: '隐藏右栏' })).toBeVisible();
    await page.getByRole('button', { name: '隐藏右栏' }).click();
    await expect(page.getByRole('button', { name: '显示右栏' })).toBeVisible();
    await expect(page.locator('.detail-panel')).toHaveCount(0);
    await page.getByRole('button', { name: '显示右栏' }).click();
    await page.locator('.detail-panel-tabs').getByRole('button', { name: '专家' }).click();
    await expect(page.getByRole('heading', { name: '产品策略团队' })).toBeVisible();
    await expect(page.getByText('快捷提示')).toBeVisible();
    await page.getByRole('button', { name: '更多操作' }).click();
    await expect(page.getByRole('menuitem', { name: '打开右栏' })).toBeVisible();
    await page.keyboard.press('Escape');
    await page.locator('.detail-panel-tabs').getByRole('button', { name: '产物' }).click();
    const commandCenterBtn = page
      .locator('.overview-team-section')
      .getByRole('button', { name: '指挥中心' });
    await expect(commandCenterBtn).toBeVisible();
    await commandCenterBtn.click();
    await expect(page.locator('.detail-preview-team-wrap .team-cc')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('团队指挥中心').first()).toBeVisible();
  });
});
