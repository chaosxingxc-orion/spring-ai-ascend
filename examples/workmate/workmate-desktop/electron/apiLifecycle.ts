import { app, BrowserWindow } from 'electron';
import { spawn, type ChildProcess } from 'node:child_process';
import path from 'node:path';

export type ApiStatus = 'starting' | 'ready' | 'error';

const HEALTH_PATHS = ['/actuator/health/readiness', '/actuator/health'];

function workmateRootFromMain(): string {
  return path.join(__dirname, '..', '..', '..');
}

async function probeHealth(apiBase: string): Promise<boolean> {
  for (const suffix of HEALTH_PATHS) {
    try {
      const response = await fetch(`${apiBase}${suffix}`, { signal: AbortSignal.timeout(2000) });
      if (response.ok) {
        return true;
      }
    } catch {
      // try next path
    }
  }
  return false;
}

function broadcastStatus(status: ApiStatus): void {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('workmate:api-status', status);
  }
}

export class ApiLifecycle {
  private child: ChildProcess | null = null;
  private readonly apiBase: string;
  private readonly autoStart: boolean;

  constructor(apiBase: string) {
    this.apiBase = apiBase.replace(/\/$/, '');
    this.autoStart = process.env.WORKMATE_SKIP_API_SPAWN !== '1';
  }

  async start(): Promise<void> {
    if (await probeHealth(this.apiBase)) {
      broadcastStatus('ready');
      return;
    }

    if (!this.autoStart) {
      broadcastStatus('error');
      return;
    }

    broadcastStatus('starting');
    const script = path.join(workmateRootFromMain(), 'scripts', 'run-local.sh');
    this.child = spawn('/bin/bash', [script], {
      cwd: workmateRootFromMain(),
      env: { ...process.env, WORKMATE_API_URL: this.apiBase },
      stdio: 'ignore',
      detached: false,
    });

    this.child.on('error', () => {
      broadcastStatus('error');
    });

    const deadline = Date.now() + 120_000;
    while (Date.now() < deadline) {
      if (await probeHealth(this.apiBase)) {
        broadcastStatus('ready');
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 500));
    }

    broadcastStatus('error');
  }

  stop(): void {
    if (this.child && !this.child.killed) {
      this.child.kill('SIGTERM');
      this.child = null;
    }
  }
}

export function attachApiLifecycleShutdown(lifecycle: ApiLifecycle): void {
  app.on('before-quit', () => {
    lifecycle.stop();
  });
}
