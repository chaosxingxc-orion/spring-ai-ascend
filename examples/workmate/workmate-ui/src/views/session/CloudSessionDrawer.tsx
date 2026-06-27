import { useCallback, useEffect, useState } from 'react';
import type { CloudSession } from '../../api/cloud';
import { getCloudSessionHealth, type CloudSessionHealth } from '../../api/cloud';
import { AUTOMATION_PATH } from '../../lib/paths';

interface CloudSessionDrawerProps {
  session: CloudSession | null;
  onClose: () => void;
  onOpenAutomation?: () => void;
}

/** W48-D5 — cloud routing details from session header badge. */
export function CloudSessionDrawer({ session, onClose, onOpenAutomation }: CloudSessionDrawerProps) {
  const [health, setHealth] = useState<CloudSessionHealth | null>(null);
  const [healthLoading, setHealthLoading] = useState(false);
  const [healthError, setHealthError] = useState<string | null>(null);

  const refreshHealth = useCallback(async () => {
    if (!session) {
      return;
    }
    setHealthLoading(true);
    setHealthError(null);
    try {
      setHealth(await getCloudSessionHealth(session.id));
    } catch (err) {
      setHealth(null);
      setHealthError((err as Error).message);
    } finally {
      setHealthLoading(false);
    }
  }, [session]);

  useEffect(() => {
    if (!session) {
      setHealth(null);
      setHealthError(null);
      return;
    }
    void refreshHealth();
  }, [session, refreshHealth]);

  if (!session) {
    return null;
  }

  return (
    <div className="drawer-backdrop" role="presentation" onClick={onClose}>
      <aside
        className="expert-drawer cloud-session-drawer"
        role="dialog"
        aria-labelledby="cloud-session-drawer-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="expert-drawer-header">
          <div className="expert-drawer-avatar" aria-hidden>☁</div>
          <div>
            <h2 id="cloud-session-drawer-title">云 Session 路由</h2>
            <p className="expert-drawer-id mono">{session.id}</p>
            <span className={`cloud-session-badge status-${session.status.toLowerCase()}`}>
              {session.status}
            </span>
          </div>
          <button type="button" className="btn ghost drawer-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>
        <div className="expert-drawer-body cloud-session-drawer-body">
          <dl className="cloud-session-drawer-meta">
            <div>
              <dt>标题</dt>
              <dd>{session.title}</dd>
            </div>
            <div>
              <dt>Runtime URL</dt>
              <dd className="mono">{session.runtimeBaseUrl ?? '—'}</dd>
            </div>
            <div>
              <dt>Sandbox ID</dt>
              <dd className="mono">{session.sandboxId ?? '—'}</dd>
            </div>
            {session.lastError && (
              <div>
                <dt>最近错误</dt>
                <dd className="cloud-session-drawer-error">{session.lastError}</dd>
              </div>
            )}
          </dl>

          <section className="cloud-session-health" aria-label="健康探活">
            <div className="cloud-session-health-head">
              <h3>健康探活</h3>
              <button
                type="button"
                className="btn ghost sm"
                disabled={healthLoading}
                onClick={() => void refreshHealth()}
              >
                {healthLoading ? '探活中…' : '重新探活'}
              </button>
            </div>
            {healthError && <p className="memory-settings-error" role="alert">{healthError}</p>}
            {health && !healthLoading && (
              <p className={health.healthy ? 'cloud-health-ok' : 'cloud-health-bad'}>
                {health.healthy ? '✓' : '✗'} {health.message}
              </p>
            )}
          </section>
        </div>
        <footer className="expert-drawer-footer">
          {onOpenAutomation && (
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                onOpenAutomation();
                onClose();
              }}
            >
              打开自动化中心
            </button>
          )}
          <a className="btn ghost" href={AUTOMATION_PATH}>
            云 Session 管理
          </a>
        </footer>
      </aside>
    </div>
  );
}
