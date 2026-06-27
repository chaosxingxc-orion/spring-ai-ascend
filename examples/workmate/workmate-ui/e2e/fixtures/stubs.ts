import type { Page } from '@playwright/test';
import { emptySessionsFixture, expertsFixture, welcomeS01Fixture } from './api';

/** Preview mode has no Vite dev proxy — stub actuator so refreshSessions() succeeds. */
export async function stubHealth(page: Page) {
  await page.route('**/actuator/health', async (route) => {
    await route.fulfill({ json: { status: 'UP' } });
  });
}

export interface CommonStubOptions {
  sessions?: unknown;
}

export async function stubCommonRoutes(page: Page, options: CommonStubOptions = {}) {
  const sessions = options.sessions ?? emptySessionsFixture;

  await stubHealth(page);

  await page.route('**/api/v1/welcome', async (route) => {
    await route.fulfill({ json: welcomeS01Fixture });
  });
  await page.route('**/api/v1/sessions', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: sessions });
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
  await page.route('**/api/v1/sessions/limits', async (route) => {
    await route.fulfill({ json: { activeCount: 0, maxActive: 50, autoArchiveOnCreate: true, archivableCount: 0 } });
  });
  await page.route('**/api/v1/tenant/quota', async (route) => {
    await route.fulfill({
      json: {
        tenantId: 'default',
        period: '2026-06',
        metrics: [],
        alerts: [],
      },
    });
  });
  await page.route('**/api/v1/cloud/sessions/by-linked/**', async (route) => {
    await route.fulfill({ status: 404, json: { message: 'not linked' } });
  });
}

/** v1.0 enterprise routes for /automation and /settings/quota E2E. */
export async function stubV10EnterpriseRoutes(page: Page) {
  await page.route('**/api/v1/automation/jobs', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        json: [
          {
            id: 'job-1',
            name: '每日研报',
            enabled: true,
            expertId: 'fund-analyst',
            promptText: '生成市场摘要',
            cronExpression: '0 9 * * *',
            nextRunAt: '2026-06-23T01:00:00Z',
            lastRunAt: null,
            lastSessionId: null,
            lastStatus: null,
            lastError: null,
            createdAt: '2026-06-22T00:00:00Z',
            updatedAt: '2026-06-22T00:00:00Z',
          },
        ],
      });
      return;
    }
    await route.continue();
  });

  await page.route('**/api/v1/cloud/sessions', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({
        json: [
          {
            id: 'cloud-1',
            expertId: 'fund-analyst',
            title: '云基金研究',
            status: 'RUNNING',
            runtimeBaseUrl: 'http://localhost:8080',
            sandboxId: 'local-stub-cloud-1',
            linkedSessionId: 'sess-linked-1',
            lastError: null,
            createdAt: '2026-06-22T00:00:00Z',
            updatedAt: '2026-06-22T00:00:00Z',
            destroyedAt: null,
          },
        ],
      });
      return;
    }
    await route.continue();
  });

  await page.route('**/api/v1/cloud/sessions/by-linked/**', async (route) => {
    await route.fulfill({
      json: {
        id: 'cloud-1',
        expertId: 'fund-analyst',
        title: '云基金研究',
        status: 'RUNNING',
        runtimeBaseUrl: 'http://localhost:8080',
        sandboxId: 'local-stub-cloud-1',
        linkedSessionId: 'sess-linked-1',
        lastError: null,
        createdAt: '2026-06-22T00:00:00Z',
        updatedAt: '2026-06-22T00:00:00Z',
        destroyedAt: null,
      },
    });
  });

  await page.route('**/api/v1/tenant/quota', async (route) => {
    await route.fulfill({
      json: {
        tenantId: 'default',
        period: '2026-06',
        metrics: [
          { key: 'activeSessions', label: '活跃会话', used: 12, limit: 50, percentUsed: 24, status: 'ok' },
          { key: 'monthlyTokens', label: '本月 Token', used: 120000, limit: 0, percentUsed: -1, status: 'ok' },
          { key: 'storageBytes', label: '存储占用', used: 1048576, limit: 0, percentUsed: -1, status: 'ok' },
        ],
        alerts: [],
      },
    });
  });

  await page.route('**/api/v1/automation/webhooks/config', async (route) => {
    await route.fulfill({
      json: {
        channels: [
          {
            id: 'generic',
            enabled: true,
            path: '/api/v1/automation/webhooks/generic',
            secretConfigured: true,
          },
          {
            id: 'feishu',
            enabled: false,
            path: '/api/v1/automation/webhooks/feishu',
            secretConfigured: false,
          },
        ],
      },
    });
  });

  await page.route('**/api/v1/automation/webhooks/deliveries**', async (route) => {
    await route.fulfill({
      json: [
        {
          id: 'wh-1',
          channel: 'generic',
          outcome: 'SKIPPED',
          sessionId: null,
          message: 'LLM not configured',
          createdAt: '2026-06-22T08:00:00Z',
        },
      ],
    });
  });

  await page.route('**/api/v1/cloud/sessions/*/health', async (route) => {
    await route.fulfill({
      json: {
        cloudSessionId: 'cloud-1',
        status: 'RUNNING',
        runtimeBaseUrl: 'http://localhost:8080',
        healthy: true,
        message: 'runtime reachable',
      },
    });
  });
}

/** Session-scoped routes shared by S11 / media / member E2E specs. */
export async function stubSessionRoutes(
  page: Page,
  sessionId: string,
  options: {
    session?: unknown;
    messages?: unknown[];
    artifacts?: unknown[];
    workspaceEntries?: unknown[];
    runEvents?: unknown[];
    teamSnapshot?: unknown | null;
  } = {},
) {
  const {
    session,
    messages = [],
    artifacts = [],
    workspaceEntries = [],
    runEvents = [],
    teamSnapshot = null,
  } = options;

  if (session) {
    await page.route(`**/api/v1/sessions/${sessionId}`, async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({ json: session });
        return;
      }
      await route.continue();
    });
  }

  await page.route(`**/api/v1/sessions/${sessionId}/messages`, async (route) => {
    await route.fulfill({ json: messages });
  });
  await page.route(`**/api/v1/sessions/${sessionId}/artifacts`, async (route) => {
    await route.fulfill({ json: artifacts });
  });
  await page.route(`**/api/v1/sessions/${sessionId}/workspace/entries**`, async (route) => {
    await route.fulfill({ json: workspaceEntries });
  });
  await page.route(`**/api/v1/sessions/${sessionId}/pending-approvals`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/v1/sessions/${sessionId}/pending-questions`, async (route) => {
    await route.fulfill({ json: [] });
  });
  await page.route(`**/api/v1/sessions/${sessionId}/run-events`, async (route) => {
    await route.fulfill({ json: runEvents });
  });
  if (teamSnapshot) {
    await page.route(`**/api/v1/sessions/${sessionId}/team-snapshot`, async (route) => {
      await route.fulfill({ json: teamSnapshot });
    });
  }
}

/** GET /api/v1/files returns a plain array (not paginated). */
export async function stubMyFiles(page: Page, files: unknown[] = []) {
  await page.route('**/api/v1/files**', async (route) => {
    if (route.request().method() === 'GET') {
      await route.fulfill({ json: files });
      return;
    }
    await route.continue();
  });
}
