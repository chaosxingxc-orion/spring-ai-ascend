import { test, expect } from '@playwright/test';
import { stubCommonRoutes, stubMyFiles } from '../fixtures/stubs';

test.describe('System pages — audit shell layout (U4 E2E)', () => {
  test.beforeEach(async ({ page }) => {
    await stubCommonRoutes(page);
  });

  test('settings keeps sidebar + main in two-column grid', async ({ page }) => {
    await page.goto('/settings');
    const shell = page.locator('.app-shell-audit');
    await expect(shell).toBeVisible();
    await expect(page.locator('.settings-page')).toBeVisible();
    await expect(page.locator('.sidebar')).toBeVisible();

    const columns = await shell.evaluate((el) => getComputedStyle(el).gridTemplateColumns);
    expect(columns.split(' ').length).toBeGreaterThanOrEqual(2);

    await expect(page.getByRole('button', { name: '设置' })).toHaveAttribute('aria-current', 'page');
  });

  test('files keeps sidebar + main in two-column grid', async ({ page }) => {
    await stubMyFiles(page);

    await page.goto('/files');
    const shell = page.locator('.app-shell-audit');
    await expect(shell).toBeVisible();
    await expect(page.locator('.myfiles-page')).toBeVisible();
    await expect(page.locator('.sidebar')).toBeVisible();

    const columns = await shell.evaluate((el) => getComputedStyle(el).gridTemplateColumns);
    expect(columns.split(' ').length).toBeGreaterThanOrEqual(2);
  });

  test('sidebar nav shells render with active nav item', async ({ page }) => {
    await page.route('**/api/v1/discover**', async (route) => {
      await route.fulfill({ json: [] });
    });

    await page.route('**/api/v1/automation/jobs', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: [] });
        return;
      }
      await route.continue();
    });
    await page.route('**/api/v1/cloud/sessions', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: [] });
        return;
      }
      await route.continue();
    });

    await page.goto('/assistant');
    await expect(page.locator('.app-shell-audit')).toBeVisible();
    await expect(page.locator('.nav-shell-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: '助理' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '最近任务' })).toBeVisible();
    await expect(page.locator('.sidebar-nav-item.active .sidebar-nav-label')).toHaveText('助理');

    await page.getByRole('button', { name: /自动化/ }).click();
    await expect(page).toHaveURL(/\/automation$/);
    await expect(page.getByRole('heading', { name: '自动化' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '定时任务' })).toBeVisible();
    await expect(page.locator('.sidebar-nav-item.active .sidebar-nav-label')).toHaveText('自动化');
  });
});

test.describe('Market header — responsive (IX-8)', () => {
  test.beforeEach(async ({ page }) => {
    await stubCommonRoutes(page);
  });

  test('expert market header stacks on narrow viewport', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 800 });
    await page.goto('/market/experts');
    await expect(page.locator('.market-page-header')).toBeVisible();
    await expect(page.getByRole('searchbox')).toBeVisible();

    const headerColumns = await page.locator('.market-page-header').evaluate((el) => {
      return getComputedStyle(el).gridTemplateColumns;
    });
    expect(headerColumns).not.toContain('auto 1fr auto');
  });
});
