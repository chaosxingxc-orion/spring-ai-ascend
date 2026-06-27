import type { PlaybookCard, WelcomeConfig } from '../types/welcome';

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

export function fetchWelcomeConfig(): Promise<WelcomeConfig> {
  return request<WelcomeConfig>('/api/v1/welcome');
}

export function listPlaybooks(placement?: string): Promise<PlaybookCard[]> {
  const query = placement ? `?placement=${encodeURIComponent(placement)}` : '';
  return request<PlaybookCard[]>(`/api/v1/playbooks${query}`);
}
