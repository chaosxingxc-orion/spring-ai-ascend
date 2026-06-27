import { useCallback, useEffect, useState } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import { listSessionChanges, readChangeDiff } from '../../api/client';
import type { FileChange, FileDiff } from '../../types/api';
import { formatDateTime } from '../../lib/formatLocale';
import { monacoEditorTheme, monacoLanguageFromPath } from '../../lib/monacoLanguage';

interface ChangesTabProps {
  sessionId: string;
  refreshKey: number;
  onOpenFile?: (path: string) => void;
  initialSelectedPath?: string | null;
}

const OP_LABEL: Record<FileChange['op'], string> = {
  created: '新建',
  modified: '修改',
  deleted: '删除',
};

export function ChangesTab({ sessionId, refreshKey, onOpenFile, initialSelectedPath }: ChangesTabProps) {
  const [changes, setChanges] = useState<FileChange[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [diff, setDiff] = useState<FileDiff | null>(null);
  const [diffLoading, setDiffLoading] = useState(false);

  const loadChanges = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await listSessionChanges(sessionId);
      setChanges(items);
      setSelectedPath((current) => {
        if (current && items.some((item) => item.path === current)) {
          return current;
        }
        return items[0]?.path ?? null;
      });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadChanges();
  }, [loadChanges, refreshKey]);

  useEffect(() => {
    if (initialSelectedPath) {
      setSelectedPath(initialSelectedPath);
    }
  }, [initialSelectedPath]);

  useEffect(() => {
    if (!selectedPath) {
      setDiff(null);
      return;
    }
    setDiffLoading(true);
    void readChangeDiff(sessionId, selectedPath)
      .then(setDiff)
      .catch((err) => setError((err as Error).message))
      .finally(() => setDiffLoading(false));
  }, [selectedPath, sessionId, refreshKey]);

  const language = diff ? monacoLanguageFromPath(diff.path, diff.mime) : 'plaintext';

  return (
    <div className="changes-tab">
      <aside className="changes-tab-list">
        <header className="changes-tab-list-header">
          <span>工作区变更</span>
          <span className="changes-tab-count">{changes.length}</span>
        </header>
        {loading && <p className="detail-hint muted">加载中…</p>}
        {!loading && changes.length === 0 && (
          <p className="detail-hint muted">暂无 Agent 写入变更</p>
        )}
        <ul className="changes-tab-items">
          {changes.map((change) => (
            <li key={`${change.path}-${change.seq}`}>
              <button
                type="button"
                className={`changes-tab-item${selectedPath === change.path ? ' active' : ''}`}
                onClick={() => setSelectedPath(change.path)}
              >
                <span className="changes-tab-item-path" title={change.path}>{change.path}</span>
                <span className={`changes-tab-op op-${change.op}`}>{OP_LABEL[change.op] ?? change.op}</span>
                <span className="changes-tab-item-meta">{formatDateTime(change.ts)}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <div className="changes-tab-diff">
        {error && <p className="detail-hint error">{error}</p>}
        {!selectedPath && !loading && (
          <div className="detail-preview-empty">
            <p>选择左侧文件查看 diff</p>
          </div>
        )}
        {selectedPath && diffLoading && (
          <p className="detail-hint muted">加载 diff…</p>
        )}
        {selectedPath && diff && !diffLoading && (
          <>
            <header className="changes-tab-diff-header">
              <span className="code-preview-path" title={diff.path}>{diff.path}</span>
              {diff.truncated && <span className="badge">已截断</span>}
              {onOpenFile && (
                <button type="button" className="btn ghost sm" onClick={() => onOpenFile(diff.path)}>
                  打开文件
                </button>
              )}
            </header>
            <div className="changes-tab-diff-editor">
              <DiffEditor
                height="100%"
                language={language}
                original={diff.original}
                modified={diff.modified}
                theme={monacoEditorTheme()}
                options={{
                  readOnly: true,
                  renderSideBySide: true,
                  minimap: { enabled: false },
                  scrollBeyondLastLine: false,
                  wordWrap: 'on',
                  fontSize: 13,
                }}
              />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
