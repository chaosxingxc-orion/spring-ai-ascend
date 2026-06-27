import { useCallback, useEffect, useState } from 'react';
import type { Expert } from '../../types/api';
import {
  createCloudSession,
  destroyCloudSession,
  getCloudSessionHealth,
  getCloudSessionManifest,
  listCloudSessions,
  sleepCloudSession,
  wakeCloudSession,
  type CloudSession,
  type CloudSessionHealth,
  type SessionManifest,
} from '../../api/cloud';
import { cloudStatusClass, cloudStatusLabel } from '../../lib/cloudSessionStatus';

interface CloudSessionsPanelProps {
  experts: Expert[];
  onOpenSession: (sessionId: string) => void;
}

export function CloudSessionsPanel({ experts, onOpenSession }: CloudSessionsPanelProps) {
  const [sessions, setSessions] = useState<CloudSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [expertId, setExpertId] = useState('');
  const [title, setTitle] = useState('');
  const [manifestPreview, setManifestPreview] = useState<SessionManifest | null>(null);
  const [healthById, setHealthById] = useState<Record<string, CloudSessionHealth>>({});
  const [healthLoading, setHealthLoading] = useState(false);

  const probeHealth = useCallback(async (items: CloudSession[]) => {
    const active = items.filter((session) => session.status !== 'DESTROYED');
    if (active.length === 0) {
      setHealthById({});
      return;
    }
    setHealthLoading(true);
    try {
      const entries = await Promise.all(
        active.map(async (session) => {
          try {
            const health = await getCloudSessionHealth(session.id);
            return [session.id, health] as const;
          } catch {
            return null;
          }
        }),
      );
      const next: Record<string, CloudSessionHealth> = {};
      for (const entry of entries) {
        if (entry) {
          next[entry[0]] = entry[1];
        }
      }
      setHealthById(next);
    } finally {
      setHealthLoading(false);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await listCloudSessions({ fresh: true });
      setSessions(items);
      await probeHealth(items);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [probeHealth]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleCreate = async () => {
    if (!expertId) {
      return;
    }
    setBusyId('create');
    setError(null);
    try {
      const created = await createCloudSession({
        expertId,
        title: title.trim() || undefined,
      });
      setTitle('');
      await refresh();
      if (created.linkedSessionId) {
        onOpenSession(created.linkedSessionId);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleWake = async (session: CloudSession) => {
    setBusyId(session.id);
    try {
      await wakeCloudSession(session.id);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleSleep = async (session: CloudSession) => {
    setBusyId(session.id);
    try {
      await sleepCloudSession(session.id);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleDestroy = async (session: CloudSession) => {
    if (!window.confirm(`销毁云 Session「${session.title}」？`)) {
      return;
    }
    setBusyId(session.id);
    try {
      await destroyCloudSession(session.id);
      setManifestPreview(null);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleShowManifest = async (session: CloudSession) => {
    setBusyId(session.id + '-manifest');
    try {
      setManifestPreview(await getCloudSessionManifest(session.id));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleProbeOne = async (session: CloudSession) => {
    setBusyId(session.id + '-health');
    try {
      const health = await getCloudSessionHealth(session.id);
      setHealthById((prev) => ({ ...prev, [session.id]: health }));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="nav-shell-section cloud-sessions-panel" aria-label="云 Session">
      <div className="cloud-sessions-panel-head">
        <div>
          <h2 className="nav-shell-section-title">云 Session（Manifest 路径 C）</h2>
          <p className="settings-panel-desc">
            状态灯 + 健康探活；关联本地会话可一键跳转。MVP 使用 local-stub Provisioner。
          </p>
        </div>
        <button
          type="button"
          className="btn ghost sm"
          disabled={loading || healthLoading || sessions.length === 0}
          onClick={() => void probeHealth(sessions)}
        >
          {healthLoading ? '探活中…' : '探活全部'}
        </button>
      </div>

      {error && <p className="market-hint error" role="alert">{error}</p>}

      <div className="automation-job-form">
        <label className="connector-connect-field">
          <span>专家</span>
          <select
            className="connector-connect-input"
            value={expertId}
            onChange={(e) => setExpertId(e.target.value)}
          >
            <option value="">选择专家</option>
            {experts.map((expert) => (
              <option key={expert.id} value={expert.id}>
                {expert.name}
              </option>
            ))}
          </select>
        </label>
        <label className="connector-connect-field">
          <span>标题（可选）</span>
          <input
            className="connector-connect-input"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="云 Session 标题"
          />
        </label>
        <button
          type="button"
          className="btn primary"
          disabled={busyId === 'create' || !expertId}
          onClick={() => void handleCreate()}
        >
          {busyId === 'create' ? '创建中…' : '创建云 Session'}
        </button>
      </div>

      {loading ? (
        <p className="nav-shell-empty muted">加载中…</p>
      ) : sessions.length === 0 ? (
        <p className="nav-shell-empty muted">暂无云 Session</p>
      ) : (
        <ul className="automation-job-list">
          {sessions.map((session) => {
            const health = healthById[session.id];
            return (
              <li key={session.id} className="automation-job-card cloud-session-card">
                <div className="automation-job-card-head">
                  <div className="cloud-session-card-title">
                    <span
                      className={`cloud-status-lamp status-${cloudStatusClass(session.status)}`}
                      title={cloudStatusLabel(session.status)}
                      aria-hidden
                    />
                    <strong>{session.title}</strong>
                  </div>
                  <span className={`cloud-session-badge status-${cloudStatusClass(session.status)}`}>
                    {cloudStatusLabel(session.status)}
                  </span>
                </div>
                <p className="muted automation-job-cron">专家: {session.expertId}</p>
                {session.runtimeBaseUrl && (
                  <p className="muted mono cloud-session-runtime">runtime: {session.runtimeBaseUrl}</p>
                )}
                {session.sandboxId && <p className="muted mono">sandbox: {session.sandboxId}</p>}
                {health && (
                  <p className={health.healthy ? 'cloud-health-ok' : 'cloud-health-bad'} role="status">
                    {health.healthy ? '✓' : '✗'} {health.message}
                  </p>
                )}
                {session.lastError && <p className="market-hint error">{session.lastError}</p>}
                {session.linkedSessionId && (
                  <button
                    type="button"
                    className="btn ghost sm"
                    onClick={() => onOpenSession(session.linkedSessionId!)}
                  >
                    打开关联会话
                  </button>
                )}
                <div className="automation-job-actions">
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busyId === session.id + '-health' || session.status === 'DESTROYED'}
                    onClick={() => void handleProbeOne(session)}
                  >
                    {busyId === session.id + '-health' ? '探活中…' : '探活'}
                  </button>
                  <button
                    type="button"
                    className="btn secondary sm"
                    disabled={busyId === session.id}
                    onClick={() => void handleShowManifest(session)}
                  >
                    Manifest
                  </button>
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busyId === session.id || session.status === 'DESTROYED'}
                    onClick={() => void handleWake(session)}
                  >
                    唤醒
                  </button>
                  <button
                    type="button"
                    className="btn ghost sm"
                    disabled={busyId === session.id || session.status === 'DESTROYED'}
                    onClick={() => void handleSleep(session)}
                  >
                    休眠
                  </button>
                  <button
                    type="button"
                    className="btn danger sm"
                    disabled={busyId === session.id || session.status === 'DESTROYED'}
                    onClick={() => void handleDestroy(session)}
                  >
                    销毁
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {manifestPreview && (
        <pre className="nav-shell-code-block cloud-manifest-preview">
          {JSON.stringify(manifestPreview, null, 2)}
        </pre>
      )}
    </section>
  );
}
