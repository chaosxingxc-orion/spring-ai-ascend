import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron';
import path from 'node:path';
import { ApiLifecycle, attachApiLifecycleShutdown } from './apiLifecycle';
import {
  relayNdjsonFile,
  relayNdjsonString,
  relayStreamableHttpSidecar,
  type AcpRelayResult,
} from './acpSidecarRelay';
import { registerOAuthProtocol } from './oauthProtocol';
import { resolveBundledUiRoot, startUiServer } from './uiServer';

const API_BASE = process.env.WORKMATE_API_URL ?? 'http://127.0.0.1:8080';
const UI_DEV_URL = process.env.WORKMATE_UI_DEV_URL ?? 'http://127.0.0.1:5174';
const DEFAULT_SIDECAR_URL = process.env.WORKMATE_ACP_SIDECAR_URL ?? '';

const apiLifecycle = new ApiLifecycle(API_BASE);
let uiServerClose: (() => void) | null = null;

function registerIpc(): void {
  ipcMain.handle('workmate:getApiBaseUrl', () => API_BASE);
  ipcMain.handle('workmate:pickWorkspaceDirectory', async () => {
    const result = await dialog.showOpenDialog({
      properties: ['openDirectory', 'createDirectory'],
    });
    if (result.canceled || result.filePaths.length === 0) {
      return null;
    }
    return result.filePaths[0] ?? null;
  });
  ipcMain.handle('workmate:openPath', async (_event, absolutePath: string) => {
    await shell.openPath(absolutePath);
  });
  ipcMain.handle(
    'workmate:relayAcpNdjson',
    async (_event, sessionId: string, ndjson: string): Promise<AcpRelayResult> =>
      relayNdjsonString(API_BASE, sessionId, ndjson),
  );
  ipcMain.handle(
    'workmate:relayAcpNdjsonFile',
    async (_event, sessionId: string, filePath: string): Promise<AcpRelayResult> =>
      relayNdjsonFile(API_BASE, sessionId, filePath),
  );
  ipcMain.handle(
    'workmate:relayStreamableHttp',
    async (_event, sessionId: string, sidecarUrl?: string): Promise<AcpRelayResult> => {
      const target = sidecarUrl?.trim() || DEFAULT_SIDECAR_URL;
      if (!target) {
        throw new Error('WORKMATE_ACP_SIDECAR_URL is not configured');
      }
      return relayStreamableHttpSidecar(API_BASE, sessionId, target);
    },
  );
}

async function resolveUiUrl(): Promise<string> {
  if (process.env.WORKMATE_UI_DEV_URL) {
    return process.env.WORKMATE_UI_DEV_URL;
  }
  if (!app.isPackaged) {
    return UI_DEV_URL;
  }
  const uiRoot = resolveBundledUiRoot();
  if (!uiRoot) {
    throw new Error('Bundled UI not found in resources/ui');
  }
  const server = await startUiServer(uiRoot);
  uiServerClose = server.close;
  return server.url;
}

function createWindow(uiUrl: string): void {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    title: 'WorkMate',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  void win.loadURL(uiUrl);
}

registerOAuthProtocol();

app.whenReady().then(async () => {
  registerIpc();
  attachApiLifecycleShutdown(apiLifecycle);
  await apiLifecycle.start();
  const uiUrl = await resolveUiUrl();
  createWindow(uiUrl);

  app.on('activate', async () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      const url = await resolveUiUrl();
      createWindow(url);
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', () => {
  uiServerClose?.();
});
