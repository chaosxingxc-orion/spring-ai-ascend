import { contextBridge, ipcRenderer } from 'electron';

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

const bridge: WorkmateDesktopBridge = {
  getApiBaseUrl: () => ipcRenderer.invoke('workmate:getApiBaseUrl'),
  pickWorkspaceDirectory: () => ipcRenderer.invoke('workmate:pickWorkspaceDirectory'),
  openPath: (absolutePath) => ipcRenderer.invoke('workmate:openPath', absolutePath),
  onApiStatus: (cb) => {
    const listener = (_event: unknown, status: 'starting' | 'ready' | 'error') => {
      cb(status);
    };
    ipcRenderer.on('workmate:api-status', listener);
    return () => {
      ipcRenderer.removeListener('workmate:api-status', listener);
    };
  },
  onOAuthCallback: (cb) => {
    const listener = (_event: unknown, payload: OAuthCallbackPayload) => {
      cb(payload);
    };
    ipcRenderer.on('workmate:oauth-callback', listener);
    return () => {
      ipcRenderer.removeListener('workmate:oauth-callback', listener);
    };
  },
  relayAcpNdjson: (sessionId, ndjson) =>
    ipcRenderer.invoke('workmate:relayAcpNdjson', sessionId, ndjson),
  relayAcpNdjsonFile: (sessionId, filePath) =>
    ipcRenderer.invoke('workmate:relayAcpNdjsonFile', sessionId, filePath),
  relayStreamableHttp: (sessionId, sidecarUrl) =>
    ipcRenderer.invoke('workmate:relayStreamableHttp', sessionId, sidecarUrl),
};

contextBridge.exposeInMainWorld('workmateDesktop', bridge);
