import type { Session, Expert, PendingApproval, ApprovalDecision, ApprovalDecisionScope, Artifact, FileContent, FileVersion, FileChange, FileDiff, WorkspaceEntry, AuditEntryPage, AuditVerifyResult, MemoryStatus, MemoryCaptureResult, TeamSnapshot, ModelCatalog, ModelEffort, SessionLimits, MyFile, MyFilesSort, MyFilesOrder, CreateSessionResult, AutoArchiveResponse, PermissionMode } from '../types/api';
import { buildApprovalDecisionBody } from '../lib/approvalScope';
import type { ChatItem } from '../types/events';
import type { CreateSessionOptions } from '../types/workspace';
import type { WorkspacePreset } from '../types/workspace';

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
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('API 返回了非 JSON 响应，请确认 workmate-api 已启动且 dev server 代理正常');
  }
  return response.json() as Promise<T>;
}

export function listSessions() {
  return request<Session[]>('/api/v1/sessions');
}

/** Lightweight sidebar list — no per-session usage rollup or office artifact paths. */
export function listSessionSummaries() {
  return request<Session[]>('/api/v1/sessions/summary');
}

export function getSession(sessionId: string) {
  return request<Session>(`/api/v1/sessions/${sessionId}`);
}

export function getSessionLimits() {
  return request<SessionLimits>('/api/v1/sessions/limits');
}

export function listExperts() {
  return request<Expert[]>('/api/v1/experts');
}

export function createSession(options?: CreateSessionOptions) {
  const body: Record<string, string | boolean | string[]> = {};
  if (options?.title) {
    body.title = options.title;
  }
  if (options?.expertId) {
    body.expertId = options.expertId;
  }
  if (options?.permissionMode) {
    body.permissionMode = options.permissionMode;
  }
  if (options?.workspacePath) {
    body.workspacePath = options.workspacePath;
  }
  if (options?.modelId) {
    body.modelId = options.modelId;
  }
  if (options?.effort) {
    body.effort = options.effort;
  }
  if (options?.gitBranch) {
    body.gitBranch = options.gitBranch;
  }
  if (options?.autoArchive !== undefined) {
    body.autoArchive = options.autoArchive;
  }
  if (options?.enabledConnectorIds && options.enabledConnectorIds.length > 0) {
    body.enabledConnectorIds = options.enabledConnectorIds;
  }
  if (options?.enabledSkillIds && options.enabledSkillIds.length > 0) {
    body.enabledSkillIds = options.enabledSkillIds;
  }
  return request<CreateSessionResult>('/api/v1/sessions', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function autoArchiveSessions(count: number) {
  return request<AutoArchiveResponse>('/api/v1/sessions/auto-archive', {
    method: 'POST',
    body: JSON.stringify({ count }),
  });
}

export function confirmPlan(sessionId: string) {
  return request<Session>(`/api/v1/sessions/${sessionId}/plan/confirm`, {
    method: 'POST',
    body: '{}',
  });
}

/** W37-B5 — edit plan steps before confirm. */
export function updatePlan(
  sessionId: string,
  planId: string,
  body: { title?: string; steps: { id: string; title: string; status?: string }[] },
) {
  return request<Record<string, unknown>>(`/api/v1/sessions/${sessionId}/plans/${planId}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  });
}

export function listModels() {
  return request<ModelCatalog>('/api/v1/models');
}

export function patchSessionMetadata(
  sessionId: string,
  patch: {
    pinned?: boolean;
    archived?: boolean;
    modelId?: string | null;
    effort?: ModelEffort | null;
    enabledConnectorIds?: string[];
    enabledSkillIds?: string[];
    permissionMode?: PermissionMode;
    expertId?: string | null;
  },
) {
  return request<Session>(`/api/v1/sessions/${sessionId}/metadata`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

/** W53 — in-session expert switch with soft handoff (generation bump + timeline card). */
export function postExpertTransition(
  sessionId: string,
  body: {
    expertId: string;
    mode?: 'SUMMON_IN_SESSION' | 'CHANGE_EXPERT' | 'SUMMON_NEW_TASK';
    enabledConnectorIds?: string[];
  },
) {
  return request<Session>(`/api/v1/sessions/${sessionId}/expert-transition`, {
    method: 'POST',
    body: JSON.stringify({
      mode: 'SUMMON_IN_SESSION',
      ...body,
    }),
  });
}

export function patchSessionConnectors(sessionId: string, enabledConnectorIds: string[]) {
  const body = JSON.stringify({ enabledConnectorIds });
  return request<Session>(`/api/v1/sessions/${sessionId}/connectors`, {
    method: 'PATCH',
    body,
  }).catch(async (err) => {
    const message = (err as Error).message;
    if (!message.includes('404') && !message.includes('Not Found')) {
      throw err;
    }
    return patchSessionMetadata(sessionId, { enabledConnectorIds });
  });
}

export function patchSessionSkills(sessionId: string, enabledSkillIds: string[]) {
  const body = JSON.stringify({ enabledSkillIds });
  return request<Session>(`/api/v1/sessions/${sessionId}/skills`, {
    method: 'PATCH',
    body,
  }).catch(async (err) => {
    const message = (err as Error).message;
    if (!message.includes('404') && !message.includes('Not Found')) {
      throw err;
    }
    return patchSessionMetadata(sessionId, { enabledSkillIds });
  });
}

export function enhancePrompt(text: string, expertId?: string, signal?: AbortSignal) {
  const body: Record<string, string> = { text };
  if (expertId) {
    body.expertId = expertId;
  }
  return request<{ text: string }>('/api/v1/prompt/enhance', {
    method: 'POST',
    body: JSON.stringify(body),
    signal,
  });
}

export function listSessionMessages(sessionId: string) {
  return request<ChatItem[]>(`/api/v1/sessions/${sessionId}/messages`);
}

export interface RecordedEventLogEntry {
  seq: number;
  name: string;
  data: unknown;
}

/**
 * Short-lived coalescing cache for the run-events history. A single session open fans this heavy
 * endpoint (up to several MB for long team runs) out across multiple effects almost simultaneously
 * — the chat hydrate, the team-timeline hydrate, syncChatFromServer, the detail panel and the
 * fallback poller. Without coalescing each one downloads + JSON.parses the entire history again,
 * which is the main source of the homepage stutter when the last session is a large research run.
 *
 * We cache the in-flight promise (and its resolved value) per session for a window shorter than the
 * 1500ms fallback poll interval, so the open burst collapses into one fetch while live polling still
 * sees fresh data on its next tick. Failures are evicted immediately so retries are not blocked.
 */
const RUN_EVENTS_CACHE_TTL_MS = 1200;
const runEventsCache = new Map<string, { promise: Promise<RecordedEventLogEntry[]>; ts: number }>();

export function invalidateRunEventsCache(sessionId?: string) {
  if (sessionId) {
    runEventsCache.delete(sessionId);
  } else {
    runEventsCache.clear();
  }
}

/**
 * 只读审计：拉取会话 run_events，用于重建团队协作时间线。
 *
 * - 不传 `after`：拉取全量历史，并走上面的合并 + 短 TTL 缓存（开局并发请求只下载一次）。
 * - 传 `after`：增量拉取 `seq > after` 的新事件，**不走缓存**（每次都要最新增量），供轮询使用，
 *   避免在长会话进行中反复下载整段历史。
 */
export function listSessionRunEvents(sessionId: string, after?: number) {
  if (typeof after === 'number') {
    return request<RecordedEventLogEntry[]>(
      `/api/v1/sessions/${sessionId}/run-events?after=${after}`,
    );
  }
  const now = Date.now();
  const cached = runEventsCache.get(sessionId);
  if (cached && now - cached.ts < RUN_EVENTS_CACHE_TTL_MS) {
    return cached.promise;
  }
  const promise = request<RecordedEventLogEntry[]>(`/api/v1/sessions/${sessionId}/run-events`);
  runEventsCache.set(sessionId, { promise, ts: now });
  promise.catch(() => {
    if (runEventsCache.get(sessionId)?.promise === promise) {
      runEventsCache.delete(sessionId);
    }
  });
  return promise;
}

/** W38 Phase 1 — ACP sessionUpdate 投影（`?view=acp`）。 */
export function listSessionRunEventsAcp(sessionId: string) {
  return request<Record<string, unknown>[]>(`/api/v1/sessions/${sessionId}/run-events?view=acp`);
}

/** W38 Phase 2 — ACP sessionUpdate[] → run_events drafts（dry-run，含 chunk 合并）。 */
export function convertAcpUpdates(sessionId: string, updates: Record<string, unknown>[]) {
  return request<{ name: string; data: Record<string, unknown> }[]>(
    `/api/v1/sessions/${sessionId}/acp/convert`,
    {
      method: 'POST',
      body: JSON.stringify(updates),
    },
  );
}

/** W38 Phase 3 — persist ACP sessionUpdate[] to run_events. */
export function ingestAcpUpdates(sessionId: string, updates: Record<string, unknown>[]) {
  return request<{ seq: number; name: string; data: Record<string, unknown> }[]>(
    `/api/v1/sessions/${sessionId}/acp/ingest`,
    {
      method: 'POST',
      body: JSON.stringify(updates),
    },
  );
}

/** W38 Phase 3 — NDJSON sidecar bridge (one sessionUpdate per line). */
export function ingestAcpNdjson(sessionId: string, ndjson: string) {
  return request<{ seq: number; name: string; data: Record<string, unknown> }[]>(
    `/api/v1/sessions/${sessionId}/acp/ingest/ndjson`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-ndjson' },
      body: ndjson,
    },
  );
}

export function listWorkspaces() {
  return request<WorkspacePreset[]>('/api/v1/workspaces');
}

export interface McpServerSummary {
  serverId: string;
  connected: boolean;
  toolCount: number;
  invalidSchemaCount?: number;
  toolsLimitWarning?: boolean;
  lastError?: string | null;
}

export function listMcpServers() {
  return request<McpServerSummary[]>('/api/v1/mcp/servers');
}

export function listPendingApprovals(sessionId: string) {
  return request<PendingApproval[]>(`/api/v1/sessions/${sessionId}/pending-approvals`);
}

export interface PendingQuestion {
  questionId: string;
  sessionId: string;
  question: string;
  options: string[];
  allowFreeText: boolean;
  multiSelect: boolean;
  toolName: string;
}

export function listPendingQuestions(sessionId: string) {
  return request<PendingQuestion[]>(`/api/v1/sessions/${sessionId}/pending-questions`);
}

export function answerQuestion(
  sessionId: string,
  questionId: string,
  body: { selections?: string[]; text?: string; skip?: boolean },
) {
  return request<{ questionId: string; sessionId: string; skipped: boolean }>(
    `/api/v1/sessions/${sessionId}/questions/${questionId}/answer`,
    {
      method: 'POST',
      body: JSON.stringify(body),
    },
  );
}

export function decideApproval(
  approvalId: string,
  decision: ApprovalDecision,
  scope?: ApprovalDecisionScope,
  /** @deprecated use scope=SESSION */
  always?: boolean,
) {
  return request<{ approvalId: string; decision: string }>(`/api/v1/approvals/${approvalId}`, {
    method: 'POST',
    body: JSON.stringify(buildApprovalDecisionBody(decision, scope, always)),
  });
}

export function listArtifacts(sessionId: string) {
  return request<Artifact[]>(`/api/v1/sessions/${sessionId}/artifacts`);
}

export function listSessionChanges(sessionId: string) {
  return request<FileChange[]>(`/api/v1/sessions/${sessionId}/changes`);
}

export function readChangeDiff(sessionId: string, path: string) {
  const query = new URLSearchParams({ path });
  return request<FileDiff>(`/api/v1/sessions/${sessionId}/changes/diff?${query.toString()}`);
}

export function listFileHistory(sessionId: string, path: string) {
  const query = new URLSearchParams({ path });
  return request<FileVersion[]>(`/api/v1/sessions/${sessionId}/files/history?${query.toString()}`);
}

export function revertFile(sessionId: string, path: string, versionId: string) {
  return request<FileVersion>(`/api/v1/sessions/${sessionId}/files/revert`, {
    method: 'POST',
    body: JSON.stringify({ path, versionId }),
  });
}

export function readFile(sessionId: string, path: string) {
  const query = new URLSearchParams({ path });
  return request<FileContent>(`/api/v1/sessions/${sessionId}/files?${query.toString()}`);
}

export function listWorkspaceEntries(sessionId: string, path = '') {
  const query = new URLSearchParams();
  if (path) {
    query.set('path', path);
  }
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return request<WorkspaceEntry[]>(`/api/v1/sessions/${sessionId}/workspace/entries${suffix}`);
}

export function searchWorkspaceFiles(sessionId: string, query = '', limit = 20) {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  return request<Artifact[]>(`/api/v1/sessions/${sessionId}/workspace/search?${params.toString()}`);
}

export interface ListAuditEntriesParams {
  category?: string;
  decision?: string;
  from?: string;
  to?: string;
  q?: string;
  cursor?: number;
  limit?: number;
}

export function listAuditEntries(params: ListAuditEntriesParams = {}) {
  const query = new URLSearchParams();
  if (params.category) query.set('category', params.category);
  if (params.decision) query.set('decision', params.decision);
  if (params.from) query.set('from', params.from);
  if (params.to) query.set('to', params.to);
  if (params.q) query.set('q', params.q);
  if (params.cursor != null) query.set('cursor', String(params.cursor));
  if (params.limit != null) query.set('limit', String(params.limit));
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return request<AuditEntryPage>(`/api/v1/admin/audit/entries${suffix}`);
}

export function verifyAuditChain(from?: number, to?: number) {
  const query = new URLSearchParams();
  if (from != null) query.set('from', String(from));
  if (to != null) query.set('to', String(to));
  const suffix = query.toString() ? `?${query.toString()}` : '';
  return request<AuditVerifyResult>(`/api/v1/admin/audit/verify${suffix}`);
}

export async function exportAuditSegment(segment: string): Promise<string> {
  const response = await fetch(`${API_BASE}/api/v1/admin/audit/export?segment=${encodeURIComponent(segment)}`);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.text();
}

export function getMemory() {
  return request<MemoryStatus>('/api/v1/memory');
}

export function updateMemorySettings(enabled: boolean, autoCapture: boolean) {
  return request<MemoryStatus>('/api/v1/memory/settings', {
    method: 'PATCH',
    body: JSON.stringify({ enabled, autoCapture }),
  });
}

export function clearMemory() {
  return request<MemoryStatus>('/api/v1/memory', { method: 'DELETE' });
}

export function rememberSession(sessionId: string) {
  return request<MemoryCaptureResult>(`/api/v1/sessions/${sessionId}/remember`, {
    method: 'POST',
    body: '{}',
  });
}

export function sendTeamMemberMessage(sessionId: string, target: string, message: string) {
  return request<{ target: string; delivered: string[] }>(
    `/api/v1/sessions/${sessionId}/team/messages`,
    {
      method: 'POST',
      body: JSON.stringify({ target, message }),
    },
  );
}

export function getTeamSnapshot(sessionId: string) {
  return request<TeamSnapshot>(`/api/v1/sessions/${sessionId}/team-snapshot`);
}

export function getRunQueueStatus(sessionId: string) {
  return request<{ depth: number; maxSize: number }>(`/api/v1/sessions/${sessionId}/run-queue`);
}

export function clearRunQueue(sessionId: string) {
  return request<{ cleared: number; depth: number }>(`/api/v1/sessions/${sessionId}/run-queue`, {
    method: 'DELETE',
  });
}

export function listMyFiles(options?: {
  q?: string;
  sort?: MyFilesSort;
  order?: MyFilesOrder;
  favoritesOnly?: boolean;
}) {
  const params = new URLSearchParams();
  if (options?.q) {
    params.set('q', options.q);
  }
  if (options?.sort) {
    params.set('sort', options.sort);
  }
  if (options?.order) {
    params.set('order', options.order);
  }
  if (options?.favoritesOnly) {
    params.set('favoritesOnly', 'true');
  }
  const query = params.toString();
  return request<MyFile[]>(`/api/v1/files${query ? `?${query}` : ''}`);
}

export function renameMyFile(sessionId: string, path: string, newName: string) {
  return request<MyFile>('/api/v1/files/rename', {
    method: 'POST',
    body: JSON.stringify({ sessionId, path, newName }),
  });
}

export function moveMyFile(sessionId: string, path: string, destPath: string) {
  return request<MyFile>('/api/v1/files/move', {
    method: 'POST',
    body: JSON.stringify({ sessionId, path, destPath }),
  });
}

export function deleteMyFile(sessionId: string, path: string) {
  return request<void>('/api/v1/files', {
    method: 'DELETE',
    body: JSON.stringify({ sessionId, path }),
  });
}

export function favoriteMyFile(sessionId: string, path: string, favorite: boolean) {
  return request<MyFile>('/api/v1/files/favorite', {
    method: 'POST',
    body: JSON.stringify({ sessionId, path, favorite }),
  });
}

export function myFileDownloadUrl(sessionId: string, path: string): string {
  const base = import.meta.env.VITE_API_BASE ?? '';
  const params = new URLSearchParams({ sessionId, path });
  return `${base}/api/v1/files/download?${params.toString()}`;
}
