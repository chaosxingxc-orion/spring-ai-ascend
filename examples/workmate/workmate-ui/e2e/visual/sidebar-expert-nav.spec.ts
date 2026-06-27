import { test, expect } from '@playwright/test';
import { emptySessionsFixture, expertsFixture, welcomeS01Fixture } from '../fixtures/api';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/welcome', async (route) => {
    await route.fulfill({ json: welcomeS01Fixture });
  });
  await page.route('**/api/v1/sessions/summary', async (route) => {
    await route.fulfill({ json: emptySessionsFixture });
  });
  await page.route('**/api/v1/experts', async (route) => {
    await route.fulfill({ json: expertsFixture });
  });
});

test('sidebar expert nav opens marketplace from new task home', async ({ page }) => {
  await page.goto('/new');
  await expect(page.locator('.chat-panel-new-task')).toBeVisible();

  await page.getByRole('button', { name: '专家 技能 / MCP' }).click();

  await expect(page).toHaveURL(/\/market\/experts$/);
  await expect(page.locator('.market-page')).toBeVisible();
  await expect(page.locator('.market-tab.active')).toHaveText('专家');
  await expect(page.locator('.sidebar-nav-item.active .sidebar-nav-label')).toHaveText('专家');
});
