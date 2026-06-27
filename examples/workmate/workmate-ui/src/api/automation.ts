const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export interface AutomationJob {
  id: string;
  name: string;
  enabled: boolean;
  expertId?: string | null;
  promptText: string;
  cronExpression: string;
  nextRunAt?: string | null;
  lastRunAt?: string | null;
  lastSessionId?: string | null;
  lastStatus?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
}

export function listAutomationJobs() {
  return request<AutomationJob[]>('/api/v1/automation/jobs');
}

export function createAutomationJob(body: {
  name: string;
  promptText: string;
  expertId?: string;
  cronExpression?: string;
  enabled?: boolean;
}) {
  return request<AutomationJob>('/api/v1/automation/jobs', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateAutomationJob(
  id: string,
  body: Partial<{
    name: string;
    promptText: string;
    expertId: string;
    cronExpression: string;
    enabled: boolean;
  }>,
) {
  return request<AutomationJob>(`/api/v1/automation/jobs/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

export function deleteAutomationJob(id: string) {
  return request<void>(`/api/v1/automation/jobs/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

export function runAutomationJobNow(id: string) {
  return request<AutomationJob>(`/api/v1/automation/jobs/${encodeURIComponent(id)}/run`, {
    method: 'POST',
  });
}

export interface WebhookChannelConfig {
  id: string;
  enabled: boolean;
  path: string;
  secretConfigured: boolean;
}

export function getWebhookConfig() {
  return request<{ channels: WebhookChannelConfig[] }>('/api/v1/automation/webhooks/config');
}

export function sendWebhookTest(
  channelId: string,
  body: Record<string, unknown>,
  secret?: string,
) {
  const headers: Record<string, string> = {};
  if (secret) {
    headers['X-WorkMate-Webhook-Secret'] = secret;
  }
  return request<unknown>(`/api/v1/automation/webhooks/${encodeURIComponent(channelId)}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
}

export interface WebhookDelivery {
  id: string;
  channel: string;
  outcome: string;
  sessionId?: string | null;
  message?: string | null;
  createdAt: string;
}

export function listWebhookDeliveries(limit = 20) {
  return request<WebhookDelivery[]>(`/api/v1/automation/webhooks/deliveries?limit=${limit}`);
}
