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

export interface DiscoverResource {
  type: string;
  id: string;
  title: string;
  subtitle?: string;
  lastUsedAt?: string;
  favorite: boolean;
}

export interface DiscoverLaunchResult {
  type: string;
  id: string;
  title: string;
  initPrompt: string;
  expertId?: string;
}

export function listDiscoverResources(favoritesOnly = false): Promise<DiscoverResource[]> {
  const query = favoritesOnly ? '?favoritesOnly=true' : '';
  return request<DiscoverResource[]>(`/api/v1/discover${query}`);
}

export function listFeaturedDiscover(): Promise<DiscoverResource[]> {
  return request<DiscoverResource[]>('/api/v1/discover/featured');
}

export function toggleDiscoverFavorite(
  type: string,
  id: string,
  favorite: boolean,
): Promise<DiscoverResource> {
  return request<DiscoverResource>(
    `/api/v1/discover/${encodeURIComponent(type)}/${encodeURIComponent(id)}/favorite?favorite=${favorite}`,
    { method: 'POST' },
  );
}

export function launchDiscoverResource(type: string, id: string): Promise<DiscoverLaunchResult> {
  return request<DiscoverLaunchResult>('/api/v1/discover/launch', {
    method: 'POST',
    body: JSON.stringify({ type, id }),
  });
}
