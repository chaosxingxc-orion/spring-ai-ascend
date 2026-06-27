import { test, expect } from '@playwright/test';
import { s11SessionFixture, s11SessionId, s11SessionsFixture } from '../fixtures/api';
import { stubCommonRoutes, stubSessionRoutes } from '../fixtures/stubs';

const mediaSessionId = 'media-preview-session';

const mediaSessionFixture = {
  ...s11SessionFixture,
  id: mediaSessionId,
  title: '媒体预览',
  expertId: 'prd-writer',
};

const mediaChatFixture = {
  items: [
    { id: 'media-user-1', kind: 'user', text: '生成一张示意图' },
    { id: 'media-assistant-1', kind: 'assistant', text: '已生成预览图。' },
  ],
  artifacts: [
    {
      path: 'dogfood-preview-test.png',
      name: 'dogfood-preview-test.png',
      mime: 'image/png',
      size: 256,
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
  workspaceEntries: [
    {
      name: 'dogfood-preview-test.png',
      path: 'dogfood-preview-test.png',
      type: 'file',
      size: 256,
      mime: 'image/png',
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
};

const tinyPng = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);

test.beforeEach(async ({ page }) => {
  await page.addInitScript(
    ({ sessionId, items }) => {
      localStorage.setItem('workmate.activeSessionId', sessionId);
      localStorage.setItem(`workmate.chat.${sessionId}`, JSON.stringify(items));
      localStorage.setItem('workmate.detailPanelVisible', 'true');
    },
    { sessionId: mediaSessionId, items: mediaChatFixture.items },
  );
  await stubCommonRoutes(page, { sessions: [{ ...mediaSessionFixture }] });
  await stubSessionRoutes(page, mediaSessionId, {
    session: mediaSessionFixture,
    messages: mediaChatFixture.items,
    artifacts: mediaChatFixture.artifacts,
    workspaceEntries: mediaChatFixture.workspaceEntries,
  });
  await page.route(`**/api/v1/sessions/${mediaSessionId}/preview/**`, async (route) => {
    await route.fulfill({
      body: tinyPng,
      contentType: 'image/png',
    });
  });
});

test('DetailPanel renders PNG preview with visible image', async ({ page }) => {
  await page.goto(`/s/${mediaSessionId}`);
  await expect(page.locator('.detail-panel')).toBeVisible();
  const filesTab = page.locator('.detail-panel-tabs').getByRole('button', { name: '全部文件' });
  await filesTab.click();
  await page.getByRole('button', { name: 'dogfood-preview-test.png' }).click();
  await expect(page.locator('.media-preview-image-stage img.media-preview-image-el')).toBeVisible();
  await expect(page.getByText('dogfood-preview-test.png').first()).toBeVisible();
});

const pdfSessionId = 'pdf-preview-session';

const pdfSessionFixture = {
  ...s11SessionFixture,
  id: pdfSessionId,
  title: 'PDF 预览',
  expertId: 'prd-writer',
};

const pdfChatFixture = {
  items: [{ id: 'pdf-user-1', kind: 'user', text: '生成 PDF' }],
  artifacts: [
    {
      path: 'sample-report.pdf',
      name: 'sample-report.pdf',
      mime: 'application/pdf',
      size: 2048,
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
  workspaceEntries: [
    {
      name: 'sample-report.pdf',
      path: 'sample-report.pdf',
      type: 'file',
      size: 2048,
      mime: 'application/pdf',
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
};

test('W50-F3 DetailPanel renders PDF preview iframe', async ({ page }) => {
  await page.addInitScript(
    ({ sessionId, items }) => {
      localStorage.setItem('workmate.activeSessionId', sessionId);
      localStorage.setItem(`workmate.chat.${sessionId}`, JSON.stringify(items));
      localStorage.setItem('workmate.detailPanelVisible', 'true');
    },
    { sessionId: pdfSessionId, items: pdfChatFixture.items },
  );
  await stubCommonRoutes(page, { sessions: [{ ...pdfSessionFixture }] });
  await stubSessionRoutes(page, pdfSessionId, {
    session: pdfSessionFixture,
    messages: pdfChatFixture.items,
    artifacts: pdfChatFixture.artifacts,
    workspaceEntries: pdfChatFixture.workspaceEntries,
  });
  await page.route(`**/api/v1/sessions/${pdfSessionId}/preview/**`, async (route) => {
    await route.fulfill({
      body: '%PDF-1.4\n%EOF',
      contentType: 'application/pdf',
    });
  });

  await page.goto(`/s/${pdfSessionId}`);
  await page.locator('.detail-panel-tabs').getByRole('button', { name: '全部文件' }).click();
  await page.getByRole('button', { name: 'sample-report.pdf' }).click();
  await expect(page.locator('.media-preview-pdf-frame')).toBeVisible();
  await expect(page.getByText('新窗口打开')).toBeVisible();
});
