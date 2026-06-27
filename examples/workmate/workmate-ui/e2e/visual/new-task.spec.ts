import { test, expect } from '@playwright/test';
import { emptySessionsFixture, expertsFixture, welcomeS01Fixture } from '../fixtures/api';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/welcome', async (route) => {
    await route.fulfill({ json: welcomeS01Fixture });
  });
  await page.route('**/api/v1/sessions', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: emptySessionsFixture });
      return;
    }
    await route.continue();
  });
  await page.route('**/api/v1/experts', async (route) => {
    await route.fulfill({ json: expertsFixture });
  });
  await page.route('**/api/v1/workspaces', async (route) => {
    await route.fulfill({
      json: [{ id: 'default', name: '默认工作空间', path: '', description: '' }],
    });
  });
});

test.describe('S01 new task — workmate-new-task-hifi.png', () => {
  test('renders hero and dock per mockup', async ({ page }) => {
    await page.goto('/new');
    await expect(page.getByRole('heading', { name: 'WorkMate，我帮你' })).toBeVisible();
    await expect(page.getByText('日常办公')).toBeVisible();
    await expect(page.getByText('总结要点')).toBeVisible();
    await expect(page.getByPlaceholder('告诉我你想做什么...')).toBeVisible();
    await expect(page.getByText('默认工作空间')).toBeVisible();
  });
});
