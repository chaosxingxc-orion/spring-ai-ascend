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

export interface IdleCapabilityItem {
  type: string;
  id: string;
  name: string;
  lastUsedAt?: string;
  idleDays: number;
}

export function listIdleCapabilities(idleDays = 30): Promise<IdleCapabilityItem[]> {
  return request<IdleCapabilityItem[]>(`/api/v1/capabilities/idle?idleDays=${idleDays}`);
}

export function disableIdleCapability(type: string, id: string): Promise<void> {
  const params = new URLSearchParams({ type, id });
  return request<void>(`/api/v1/capabilities/idle/disable?${params.toString()}`, { method: 'POST', body: '{}' });
}
