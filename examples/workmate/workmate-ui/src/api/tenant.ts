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

export interface QuotaMetric {
  key: string;
  label: string;
  used: number;
  limit: number;
  percentUsed: number;
  status: 'ok' | 'warn' | 'exceeded';
}

export interface QuotaAlert {
  level: 'warn' | 'exceeded';
  metric: string;
  message: string;
}

export interface TenantQuota {
  tenantId: string;
  period: string;
  metrics: QuotaMetric[];
  alerts: QuotaAlert[];
}

export function getTenantQuota() {
  return request<TenantQuota>('/api/v1/tenant/quota');
}
