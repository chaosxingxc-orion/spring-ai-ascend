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

export interface CloudSession {
  id: string;
  expertId: string;
  title: string;
  status: string;
  runtimeBaseUrl?: string | null;
  sandboxId?: string | null;
  linkedSessionId?: string | null;
  lastError?: string | null;
  createdAt: string;
  updatedAt: string;
  destroyedAt?: string | null;
}

export interface SessionManifest {
  apiVersion: string;
  kind: string;
  metadata: {
    cloudSessionId: string;
    expertId: string;
    title: string;
    createdAt: string;
  };
  spec: {
    runtimeType: string;
    runtime: { image: string; sandboxProfile: string };
    workspace: { mountPath: string; storageClass: string };
    agent: { expertId: string; expertType: string; permissionMode: string };
  };
}

let cloudSessionsCache: CloudSession[] | null = null;
let cloudSessionsInflight: Promise<CloudSession[]> | null = null;

export function invalidateCloudSessionsCache(): void {
  cloudSessionsCache = null;
  cloudSessionsInflight = null;
}

function isActiveCloudSession(session: CloudSession): boolean {
  return session.status.toUpperCase() !== 'DESTROYED';
}

async function loadCloudSessionsCached(fresh = false): Promise<CloudSession[]> {
  if (!fresh && cloudSessionsCache) {
    return cloudSessionsCache;
  }
  if (!fresh && cloudSessionsInflight) {
    return cloudSessionsInflight;
  }
  cloudSessionsInflight = request<CloudSession[]>('/api/v1/cloud/sessions')
    .then((sessions) => {
      cloudSessionsCache = sessions;
      cloudSessionsInflight = null;
      return sessions;
    })
    .catch((error) => {
      cloudSessionsInflight = null;
      throw error;
    });
  return cloudSessionsInflight;
}

export function listCloudSessions(options?: { fresh?: boolean }) {
  return loadCloudSessionsCached(Boolean(options?.fresh));
}

export function createCloudSession(body: {
  expertId: string;
  title?: string;
  permissionMode?: string;
}) {
  return request<CloudSession>('/api/v1/cloud/sessions', {
    method: 'POST',
    body: JSON.stringify(body),
  }).then((session) => {
    invalidateCloudSessionsCache();
    return session;
  });
}

export function getCloudSessionManifest(id: string) {
  return request<SessionManifest>(`/api/v1/cloud/sessions/${encodeURIComponent(id)}/manifest`);
}

export function wakeCloudSession(id: string) {
  return request<CloudSession>(`/api/v1/cloud/sessions/${encodeURIComponent(id)}/wake`, {
    method: 'POST',
  }).then((session) => {
    invalidateCloudSessionsCache();
    return session;
  });
}

export function sleepCloudSession(id: string) {
  return request<CloudSession>(`/api/v1/cloud/sessions/${encodeURIComponent(id)}/sleep`, {
    method: 'POST',
  }).then((session) => {
    invalidateCloudSessionsCache();
    return session;
  });
}

export function destroyCloudSession(id: string) {
  return request<void>(`/api/v1/cloud/sessions/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  }).then((result) => {
    invalidateCloudSessionsCache();
    return result;
  });
}

/** Resolve a local session's linked cloud session without probing by-linked (avoids 404 noise). */
export async function getCloudSessionByLinked(linkedSessionId: string): Promise<CloudSession | null> {
  const sessions = await loadCloudSessionsCached();
  return (
    sessions.find(
      (session) =>
        session.linkedSessionId === linkedSessionId && isActiveCloudSession(session),
    ) ?? null
  );
}

export interface CloudSessionHealth {
  cloudSessionId: string;
  status: string;
  runtimeBaseUrl?: string | null;
  healthy: boolean;
  message: string;
}

export function getCloudSessionHealth(id: string) {
  return request<CloudSessionHealth>(
    `/api/v1/cloud/sessions/${encodeURIComponent(id)}/health`,
  );
}
