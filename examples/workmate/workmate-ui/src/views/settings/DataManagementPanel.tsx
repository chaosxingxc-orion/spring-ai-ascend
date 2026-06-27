import { useCallback, useEffect, useState } from 'react';
import {
  clearArchivedSessions,
  exportDataArchive,
  formatBytes,
  getStorageUsage,
  type StorageUsage,
} from '../../api/data';

export function DataManagementPanel() {
  const [usage, setUsage] = useState<StorageUsage | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<'export' | 'clear' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsage(await getStorageUsage());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleExport = async () => {
    setBusy('export');
    setError(null);
    setMessage(null);
    try {
      await exportDataArchive();
      setMessage('数据包已开始下载');
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const handleClear = async () => {
    if (!window.confirm('确定删除全部已归档会话？此操作不可恢复。')) {
      return;
    }
    setBusy('clear');
    setError(null);
    setMessage(null);
    try {
      const result = await clearArchivedSessions();
      setMessage(`已清理 ${result.removedSessions} 个归档会话`);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="settings-panel data-management-panel">
      <p className="settings-panel-desc">
        导出本地 data 与 workspace 目录，查看存储占用，或清理已归档会话记录。
      </p>

      {error && <p className="memory-settings-error" role="alert">{error}</p>}
      {message && <p className="settings-field-hint" role="status">{message}</p>}

      {loading ? (
        <p className="memory-settings-empty">加载中…</p>
      ) : usage ? (
        <>
          <dl className="data-usage-grid">
            <div>
              <dt>数据目录</dt>
              <dd>{formatBytes(usage.dataBytes)}</dd>
            </div>
            <div>
              <dt>工作区</dt>
              <dd>{formatBytes(usage.workspaceBytes)}</dd>
            </div>
            <div>
              <dt>会话总数</dt>
              <dd>{usage.sessionCount}</dd>
            </div>
            <div>
              <dt>已归档</dt>
              <dd>{usage.archivedSessionCount}</dd>
            </div>
          </dl>

          <div className="settings-actions-row">
            <button
              type="button"
              className="btn primary"
              disabled={busy !== null}
              onClick={() => void handleExport()}
            >
              {busy === 'export' ? '导出中…' : '导出 ZIP'}
            </button>
            <button
              type="button"
              className="btn ghost"
              disabled={busy !== null || usage.archivedSessionCount === 0}
              onClick={() => void handleClear()}
            >
              {busy === 'clear' ? '清理中…' : '清理归档会话'}
            </button>
          </div>
        </>
      ) : null}
    </div>
  );
}
