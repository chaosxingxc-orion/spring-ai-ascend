import type {
  ConnectorAuthProfile,
  ConnectorInfo,
  MarketplaceInfo,
  OAuthDeviceCodePoll,
  OAuthDeviceCodeStart,
  OAuthRedirectStart,
  PluginInfo,
  SkillInfo,
} from '../types/market';

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

export function listSkills(): Promise<SkillInfo[]> {
  return request<SkillInfo[]>('/api/v1/skills');
}

export function installSkill(skillId: string): Promise<SkillInfo> {
  return request<SkillInfo>(`/api/v1/skills/${encodeURIComponent(skillId)}/install`, {
    method: 'POST',
    body: '{}',
  });
}

export interface SkillScanResult {
  skillId: string;
  safe: boolean;
  warnings: string[];
}

export function scanSkillSecurity(skillId: string): Promise<SkillScanResult> {
  return request<SkillScanResult>(`/api/v1/skills/${encodeURIComponent(skillId)}/security-scan`);
}

export function uninstallSkill(skillId: string): Promise<SkillInfo> {
  return request<SkillInfo>(`/api/v1/skills/${encodeURIComponent(skillId)}/uninstall`, {
    method: 'POST',
    body: '{}',
  });
}

export function listConnectors(): Promise<ConnectorInfo[]> {
  return request<ConnectorInfo[]>('/api/v1/connectors');
}

export function getConnectorAuthProfile(connectorId: string): Promise<ConnectorAuthProfile> {
  return request<ConnectorAuthProfile>(`/api/v1/connectors/${encodeURIComponent(connectorId)}/auth`);
}

export function connectConnector(
  connectorId: string,
  body?: { apiKey?: string },
): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(`/api/v1/connectors/${encodeURIComponent(connectorId)}/connect`, {
    method: 'POST',
    body: JSON.stringify(body ?? {}),
  });
}

export function disconnectConnector(connectorId: string): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/disconnect`,
    { method: 'POST', body: '{}' },
  );
}

export function reconnectConnector(connectorId: string): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/reconnect`,
    { method: 'POST', body: '{}' },
  );
}

export function revokeConnector(connectorId: string): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/revoke`,
    { method: 'POST', body: '{}' },
  );
}

export function startOAuthRedirect(connectorId: string): Promise<OAuthRedirectStart> {
  return request<OAuthRedirectStart>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/oauth/redirect/start`,
    { method: 'POST', body: '{}' },
  );
}

export function completeOAuthCallback(state: string, code: string): Promise<ConnectorInfo> {
  return request<ConnectorInfo>('/api/v1/connectors/oauth/callback', {
    method: 'POST',
    body: JSON.stringify({ state, code }),
  });
}

export function startOAuthDeviceCode(
  connectorId: string,
  method: 'DEVICE_CODE' | 'QR' = 'DEVICE_CODE',
): Promise<OAuthDeviceCodeStart> {
  const query = method === 'QR' ? '?method=QR' : '';
  return request<OAuthDeviceCodeStart>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/oauth/device-code/start${query}`,
    { method: 'POST', body: '{}' },
  );
}

export function pollOAuthDeviceCode(sessionId: string): Promise<OAuthDeviceCodePoll> {
  return request<OAuthDeviceCodePoll>(
    `/api/v1/connectors/oauth/device-code/${encodeURIComponent(sessionId)}/poll`,
  );
}

export function completeOAuthDeviceCode(
  sessionId: string,
  body: { apiKey?: string; token?: string },
): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(
    `/api/v1/connectors/oauth/device-code/${encodeURIComponent(sessionId)}/complete`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

export function storeOAuthToken(
  connectorId: string,
  body: { apiKey?: string; token?: string },
): Promise<ConnectorInfo> {
  return request<ConnectorInfo>(
    `/api/v1/connectors/${encodeURIComponent(connectorId)}/oauth/token`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

export function listMarketplaces(): Promise<MarketplaceInfo[]> {
  return request<MarketplaceInfo[]>('/api/v1/marketplaces');
}

export function addMarketplace(body: {
  id: string;
  name: string;
  sourceType?: string;
  sourceUri?: string;
}): Promise<MarketplaceInfo> {
  return request<MarketplaceInfo>('/api/v1/marketplaces', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function refreshMarketplace(marketplaceId: string): Promise<MarketplaceInfo> {
  return request<MarketplaceInfo>(
    `/api/v1/marketplaces/${encodeURIComponent(marketplaceId)}/refresh`,
    { method: 'POST', body: '{}' },
  );
}

export function deleteMarketplace(marketplaceId: string): Promise<void> {
  return request<void>(`/api/v1/marketplaces/${encodeURIComponent(marketplaceId)}`, {
    method: 'DELETE',
  });
}

export function listPlugins(): Promise<PluginInfo[]> {
  return request<PluginInfo[]>('/api/v1/plugins');
}

export function installPlugin(marketplaceId: string, pluginId: string): Promise<PluginInfo> {
  return request<PluginInfo>(
    `/api/v1/plugins/${encodeURIComponent(marketplaceId)}/${encodeURIComponent(pluginId)}/install`,
    { method: 'POST', body: '{}' },
  );
}

export function uninstallPlugin(marketplaceId: string, pluginId: string): Promise<PluginInfo> {
  return request<PluginInfo>(
    `/api/v1/plugins/${encodeURIComponent(marketplaceId)}/${encodeURIComponent(pluginId)}/uninstall`,
    { method: 'POST', body: '{}' },
  );
}

export function updatePlugin(marketplaceId: string, pluginId: string): Promise<PluginInfo> {
  return request<PluginInfo>(
    `/api/v1/plugins/${encodeURIComponent(marketplaceId)}/${encodeURIComponent(pluginId)}/update`,
    { method: 'POST', body: '{}' },
  );
}
