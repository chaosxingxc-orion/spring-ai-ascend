import { useCallback, useEffect, useState } from 'react';
import { listFileHistory, revertFile } from '../../api/client';
import type { FileVersion } from '../../types/api';
import { formatDateTime, formatFileSize, useLocaleKey } from '../../lib/formatLocale';

interface FileVersionPanelProps {
  sessionId: string;
  path: string;
  refreshKey: number;
  onReverted?: () => void;
}

const OP_LABEL: Record<FileVersion['op'], string> = {
  created: '创建',
  modified: '修改',
  deleted: '删除',
};

export function FileVersionPanel({ sessionId, path, refreshKey, onReverted }: FileVersionPanelProps) {
  const localeKey = useLocaleKey();
  const [versions, setVersions] = useState<FileVersion[]>([]);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await listFileHistory(sessionId, path);
      setVersions(items.slice().reverse());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [path, sessionId]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const handleRevert = async (version: FileVersion) => {
    setBusyId(version.versionId);
    setError(null);
    try {
      await revertFile(sessionId, path, version.versionId);
      onReverted?.();
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  if (loading && versions.length === 0) {
    return <p className="detail-hint muted">加载版本…</p>;
  }

  if (versions.length === 0) {
    return <p className="detail-hint muted">暂无历史版本</p>;
  }

  return (
    <section className="file-version-panel" aria-label="文件版本" key={localeKey}>
      {error && <p className="detail-hint error">{error}</p>}
      <ol className="file-version-list">
        {versions.map((version) => (
          <li key={version.versionId} className="file-version-item">
            <div className="file-version-meta">
              <span className={`file-version-op op-${version.op}`}>{OP_LABEL[version.op]}</span>
              <span className="file-version-ts">{formatDateTime(version.ts)}</span>
              {version.bytes > 0 && (
                <span className="file-version-bytes">{formatFileSize(version.bytes)}</span>
              )}
            </div>
            <button
              type="button"
              className="btn ghost sm"
              disabled={busyId === version.versionId}
              onClick={() => void handleRevert(version)}
            >
              回退到此版本
            </button>
          </li>
        ))}
      </ol>
    </section>
  );
}
