export interface AcpRelayResult {
  ingested: number;
  events: { seq: number; name: string; data: Record<string, unknown> }[];
}

export interface OAuthCallbackPayload {
  state: string;
  code: string;
}

export interface WorkmateDesktopBridge {
  getApiBaseUrl(): Promise<string>;
  pickWorkspaceDirectory(): Promise<string | null>;
  openPath(absolutePath: string): Promise<void>;
  onApiStatus(cb: (status: 'starting' | 'ready' | 'error') => void): () => void;
  onOAuthCallback(cb: (payload: OAuthCallbackPayload) => void): () => void;
  relayAcpNdjson(sessionId: string, ndjson: string): Promise<AcpRelayResult>;
  relayAcpNdjsonFile(sessionId: string, filePath: string): Promise<AcpRelayResult>;
  relayStreamableHttp(sessionId: string, sidecarUrl?: string): Promise<AcpRelayResult>;
}
