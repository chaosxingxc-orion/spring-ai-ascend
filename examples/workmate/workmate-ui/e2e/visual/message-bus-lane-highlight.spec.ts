import { test, expect } from '@playwright/test';
import {
  busChatFixture,
  busExpertFixture,
  busRunEvents,
  busSessionFixture,
  busSessionId,
  busSessionsFixture,
  expertsFixture,
} from '../fixtures/api';
import { stubCommonRoutes, stubSessionRoutes } from '../fixtures/stubs';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(
    ({ sessionId, items }) => {
      localStorage.setItem('workmate.activeSessionId', sessionId);
      localStorage.setItem(`workmate.chat.${sessionId}`, JSON.stringify(items));
    },
    { sessionId: busSessionId, items: busChatFixture.items },
  );

  await stubCommonRoutes(page, { sessions: busSessionsFixture });
  await page.route('**/api/v1/experts', async (route) => {
    await route.fulfill({ json: [...expertsFixture, busExpertFixture] });
  });
  await stubSessionRoutes(page, busSessionId, {
    session: busSessionFixture,
    messages: busChatFixture.items,
    artifacts: busChatFixture.artifacts,
    workspaceEntries: busChatFixture.workspaceEntries,
    runEvents: busRunEvents,
  });
});

test('timeline bus bubble click highlights matching EventLanes card', async ({ page }) => {
  await page.goto(`/s/${busSessionId}`);

  await expect(page.getByText('团队协作动态')).toBeVisible();
  await expect(page.locator('.tool-card-bus-publish')).toBeVisible();

  await page.locator('.tool-card-bus-publish').first().click();

  const highlighted = page.locator('.team-event-lane-card-highlighted');
  await expect(highlighted).toBeVisible();
  await expect(highlighted).toContainText('市场快讯要点摘要');
  await expect(page.locator('.tool-card-bus-publish.lane-linked')).toBeVisible();
});
