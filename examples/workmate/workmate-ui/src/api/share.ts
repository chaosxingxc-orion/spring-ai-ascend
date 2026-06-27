export type ShareScope = 'full' | 'messages' | 'artifacts';

export interface ShareLink {
  token: string;
  sharePath: string;
  scope?: ShareScope;
  expiresAt?: string | null;
}

export interface ShareCreateOptions {
  scope?: ShareScope;
  expiresInHours?: number;
}

export interface ShareArtifactSummary {
  path: string;
  name: string;
  mime: string;
  size: number;
  updatedAt: string;
}

export interface ShareReplay {
  token: string;
  sessionId: string;
  title: string;
  expertId?: string | null;
  sharedAt: string;
  scope?: ShareScope;
  expiresAt?: string | null;
  messages: unknown[];
  events: Array<{ seq: number; name: string; data?: Record<string, unknown> }>;
  artifacts: ShareArtifactSummary[];
}

export interface TempFileShareLink {
  token: string;
  downloadPath: string;
  expiresAt: string;
}

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
  return response.json() as Promise<T>;
}

export function createSessionShare(sessionId: string, options?: ShareCreateOptions) {
  return request<ShareLink>(`/api/v1/sessions/${sessionId}/share`, {
    method: 'POST',
    body: JSON.stringify({
      scope: options?.scope ?? 'full',
      expiresInHours: options?.expiresInHours ?? 168,
    }),
  });
}

export function getShareReplay(token: string) {
  return request<ShareReplay>(`/api/v1/share/${encodeURIComponent(token)}`);
}

export function createTempFileShare(sessionId: string, path: string, expiresInHours = 72) {
  return request<TempFileShareLink>('/api/v1/files/share', {
    method: 'POST',
    body: JSON.stringify({ sessionId, path, expiresInHours }),
  });
}

export function buildShareUrl(origin: string, token: string): string {
  return `${origin.replace(/\/$/, '')}/share/${token}`;
}

export function buildShareArtifactDownloadUrl(token: string, path: string): string {
  const params = new URLSearchParams({ path });
  return `${API_BASE}/api/v1/share/${encodeURIComponent(token)}/download?${params.toString()}`;
}

export function buildTempFileDownloadUrl(origin: string, token: string): string {
  const base = API_BASE || origin.replace(/\/$/, '');
  return `${base}/api/v1/share/files/${encodeURIComponent(token)}`;
}
