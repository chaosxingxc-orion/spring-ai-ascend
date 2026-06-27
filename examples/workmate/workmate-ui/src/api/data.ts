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

export interface StorageUsage {
  dataBytes: number;
  workspaceBytes: number;
  sessionCount: number;
  archivedSessionCount: number;
}

export interface DataClearResult {
  removedSessions: number;
}

export function getStorageUsage(): Promise<StorageUsage> {
  return request<StorageUsage>('/api/v1/data/usage');
}

export async function exportDataArchive(): Promise<void> {
  const response = await fetch(`${API_BASE}/api/v1/data/export`);
  if (!response.ok) {
    throw new Error(`Export failed: HTTP ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'workmate-export.zip';
  anchor.click();
  URL.revokeObjectURL(url);
}

export function clearArchivedSessions(): Promise<DataClearResult> {
  return request<DataClearResult>('/api/v1/data/clear-archived', { method: 'POST' });
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  if (bytes < 1024 * 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}
