import { test, expect } from '@playwright/test';
import {
  expertsFixture,
  s11ChatFixture,
  s11SessionFixture,
  s11SessionId,
  s11SessionsFixture,
} from '../fixtures/api';
import { stubCommonRoutes } from '../fixtures/stubs';

const memberRunEvents = [
  {
    seq: 1,
    name: 'tool.start',
    data: {
      toolName: 'Write',
      toolCallId: 'tc-write-1',
      surface: 'team',
      memberId: 'prd-writer',
      parentRunId: 'parent-run-1',
      args: { path: 'notes.md' },
    },
  },
  {
    seq: 2,
    name: 'tool.end',
    data: {
      toolName: 'Write',
      toolCallId: 'tc-write-1',
      surface: 'team',
      memberId: 'prd-writer',
      parentRunId: 'parent-run-1',
      result: { success: true },
    },
  },
  {
    seq: 3,
    name: 'message.delta',
    data: {
      surface: 'team',
      memberId: 'prd-writer',
      parentRunId: 'parent-run-1',
      text: '已完成竞品要点整理。',
    },
  },
];

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
  await page.route(`**/api/v1/sessions/${s11SessionId}/run-events`, async (route) => {
    await route.fulfill({ json: memberRunEvents });
  });
});

test('AgentMemberViewer shows member tool trace from run_events', async ({ page }) => {
  await page.goto(`/s/${s11SessionId}`);
  await page.getByRole('button', { name: 'PRD 写手' }).click();
  await expect(page.getByRole('heading', { name: 'PRD 写手 · 成员轨迹' })).toBeVisible();
  await expect(page.locator('.agent-member-trace-list').first()).toContainText('notes.md');
  await expect(page.getByText('已完成竞品要点整理。')).toBeVisible();
});
