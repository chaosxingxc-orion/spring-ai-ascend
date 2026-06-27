export type MarketTab = 'experts' | 'skills' | 'connectors';

export type AppView = 'workbench' | 'market';

export type ConnectorAuthMethod = 'NONE' | 'API_KEY' | 'REDIRECT' | 'DEVICE_CODE' | 'QR' | 'CLI_LOGIN';

export interface ConnectorInfo {
  id: string;
  name: string;
  description: string;
  status: 'connected' | 'disconnected' | 'connecting' | 'error';
  toolCount?: number;
  requiresAuth?: boolean;
  authHint?: string | null;
  authMethod?: ConnectorAuthMethod;
  hasCredential?: boolean;
  credentialMask?: string | null;
  invalidSchemaCount?: number;
  toolsLimitWarning?: boolean;
  lastError?: string | null;
  runnable?: boolean;
  source?: string;
}

export interface ConnectorAuthProfile {
  connectorId: string;
  authMethod: ConnectorAuthMethod;
  hasCredential: boolean;
  credentialMask?: string | null;
}

export interface OAuthRedirectStart {
  authorizeUrl: string;
  state: string;
  expiresIn: number;
}

export interface OAuthDeviceCodeStart {
  sessionId: string;
  userCode: string;
  deviceCode: string;
  verificationUri: string;
  method: string;
  expiresIn: number;
}

export interface OAuthDeviceCodePoll {
  status: 'pending' | 'approved' | 'denied' | 'expired';
  connectorId: string;
}

export interface SkillInfo {
  id: string;
  name: string;
  description: string;
  category?: string;
  tags?: string[];
  source?: string;
  installed: boolean;
  /** Built-in skills marked defaultInstalled in office/skills — cannot uninstall. */
  policyLocked?: boolean;
}

export interface MarketplaceInfo {
  id: string;
  name: string;
  sourceType: string;
  sourceUri?: string | null;
  builtin: boolean;
  pluginCount: number;
}

export interface PluginInfo {
  marketplaceId: string;
  marketplaceName: string;
  id: string;
  name: string;
  description: string;
  version: string;
  category?: string;
  installed: boolean;
  policyLocked: boolean;
  updateAvailable: boolean;
}

export interface ImportValidation {
  valid: boolean;
  message: string;
}
