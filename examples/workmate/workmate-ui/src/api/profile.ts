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

export interface UserProfile {
  role: string;
  interests: string[];
}

export function getUserProfile(): Promise<UserProfile> {
  return request<UserProfile>('/api/v1/user/profile');
}

export function saveUserProfile(profile: UserProfile): Promise<UserProfile> {
  return request<UserProfile>('/api/v1/user/profile', {
    method: 'PUT',
    body: JSON.stringify(profile),
  });
}
