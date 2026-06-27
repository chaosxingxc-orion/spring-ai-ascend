import { app, BrowserWindow } from 'electron';

export interface OAuthCallbackPayload {
  state: string;
  code: string;
}

function parseOAuthCallback(url: string): OAuthCallbackPayload | null {
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'workmate:') {
      return null;
    }
    const hostPath = `${parsed.hostname}${parsed.pathname}`;
    if (!hostPath.startsWith('oauth')) {
      return null;
    }
    const state = parsed.searchParams.get('state');
    const code = parsed.searchParams.get('code');
    if (!state || !code) {
      return null;
    }
    return { state, code };
  } catch {
    return null;
  }
}

function broadcastOAuthCallback(payload: OAuthCallbackPayload): void {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('workmate:oauth-callback', payload);
  }
}

function handleDeepLink(url: string): void {
  const payload = parseOAuthCallback(url);
  if (payload) {
    broadcastOAuthCallback(payload);
  }
}

/** Register `workmate://oauth/callback` deep link (Desktop OAuth walkthrough). */
export function registerOAuthProtocol(): void {
  if (process.platform === 'darwin') {
    app.on('open-url', (event, url) => {
      event.preventDefault();
      handleDeepLink(url);
    });
  }

  const gotLock = app.requestSingleInstanceLock();
  if (!gotLock) {
    app.quit();
    return;
  }

  app.on('second-instance', (_event, argv) => {
    const deepLink = argv.find((arg) => arg.startsWith('workmate://'));
    if (deepLink) {
      handleDeepLink(deepLink);
    }
  });

  if (process.platform === 'win32' || process.platform === 'linux') {
    const deepLink = process.argv.find((arg) => arg.startsWith('workmate://'));
    if (deepLink) {
      app.whenReady().then(() => handleDeepLink(deepLink));
    }
  }

  if (!app.isDefaultProtocolClient('workmate')) {
    app.setAsDefaultProtocolClient('workmate');
  }
}
