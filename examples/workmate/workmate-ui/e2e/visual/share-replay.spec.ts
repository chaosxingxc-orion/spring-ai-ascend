import { test, expect } from '@playwright/test';
import { stubHealth } from '../fixtures/stubs';

const shareToken = 'demo-share-token';

const shareReplayFixture = {
  token: shareToken,
  sessionId: '550e8400-e29b-41d4-a716-446655440099',
  title: '演示分享任务',
  expertId: 'prd-writer',
  sharedAt: '2026-06-22T08:00:00.000Z',
  scope: 'full',
  expiresAt: '2026-06-29T08:00:00.000Z',
  messages: [
    { kind: 'user', seq: 1, id: 'u1', text: '你好' },
    { kind: 'assistant', seq: 2, id: 'a1', text: '你好，这是分享回放。' },
  ],
  events: [],
  artifacts: [
    {
      path: 'report.pdf',
      name: 'report.pdf',
      mime: 'application/pdf',
      size: 1024,
      updatedAt: '2026-06-22T08:00:00.000Z',
    },
  ],
};

test.beforeEach(async ({ page }) => {
  await stubHealth(page);
  await page.route(`**/api/v1/share/${shareToken}`, async (route) => {
    await route.fulfill({ json: shareReplayFixture });
  });
  await page.route(`**/api/v1/share/${shareToken}/download**`, async (route) => {
    await route.fulfill({ body: '%PDF-1.4 demo', contentType: 'application/pdf' });
  });
});

test('W49-E1 share page is standalone without sidebar', async ({ page }) => {
  await page.goto(`/share/${shareToken}`);
  await expect(page.locator('.share-replay-view')).toBeVisible();
  await expect(page.locator('.sidebar')).toHaveCount(0);
  await expect(page.getByRole('heading', { name: '演示分享任务' })).toBeVisible();
  await expect(page.getByText('你好，这是分享回放。')).toBeVisible();
  await expect(page.getByRole('link', { name: '下载' })).toBeVisible();
});
