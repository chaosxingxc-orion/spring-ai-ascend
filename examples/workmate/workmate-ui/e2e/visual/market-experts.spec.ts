import { test, expect } from '@playwright/test';
import { emptySessionsFixture, expertsFixture, welcomeS01Fixture } from '../fixtures/api';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/welcome', async (route) => {
    await route.fulfill({ json: welcomeS01Fixture });
  });
  await page.route('**/api/v1/sessions', async (route) => {
    await route.fulfill({ json: emptySessionsFixture });
  });
  await page.route('**/api/v1/experts', async (route) => {
    await route.fulfill({ json: expertsFixture });
  });
});

test.describe('S06 expert market — workmate-expert-marketplace-hifi.png', () => {
  test('renders expert tabs without featured section', async ({ page }) => {
    await page.goto('/market/experts');
    await expect(page.locator('.market-tab.active')).toHaveText('专家');
    await expect(page.getByText('精选推荐')).toHaveCount(0);
    await expect(page.getByRole('tab', { name: '专家团' })).toBeVisible();
    await page.getByRole('tab', { name: '专家团' }).click();
    await expect(page.locator('.expert-card').filter({ hasText: '产品策略团队' })).toBeVisible();
    await expect(page.locator('.expert-card').filter({ hasText: 'PRD 写手' })).toHaveCount(0);
  });
});
