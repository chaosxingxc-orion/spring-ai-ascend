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

export interface SecurityPolicy {
  domainAllowList: string[];
  domainDenyList: string[];
  bashAskPatterns: string[];
  bashBlockPatterns: string[];
  fileBlockPatterns: string[];
}

export interface NetworkCheckResult {
  target: string;
  reachable: boolean;
  latencyMs: number;
  detail: string;
  policyAllowed?: boolean;
}

export interface NetworkCheckReport {
  proxyMode: string;
  checks: NetworkCheckResult[];
}

export function getSecurityPolicy(): Promise<SecurityPolicy> {
  return request<SecurityPolicy>('/api/v1/security/policy');
}

export function updateSecurityPolicy(policy: SecurityPolicy): Promise<SecurityPolicy> {
  return request<SecurityPolicy>('/api/v1/security/policy', {
    method: 'PUT',
    body: JSON.stringify(policy),
  });
}

export function resetSecurityPolicy(): Promise<SecurityPolicy> {
  return request<SecurityPolicy>('/api/v1/security/policy/reset', { method: 'POST' });
}

export function runNetworkCheck(): Promise<NetworkCheckReport> {
  return request<NetworkCheckReport>('/api/v1/diag/network');
}
