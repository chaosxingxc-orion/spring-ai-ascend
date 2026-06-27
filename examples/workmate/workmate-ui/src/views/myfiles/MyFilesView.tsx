import { useCallback, useEffect, useState } from 'react';
import {
  deleteMyFile,
  favoriteMyFile,
  listMyFiles,
  moveMyFile,
  myFileDownloadUrl,
  renameMyFile,
} from '../../api/client';
import type { MyFile, MyFilesOrder, MyFilesSort } from '../../types/api';
import { FileRow, type MyFileAction } from '../../components/myfiles/FileRow';
import { FilePathPromptModal } from '../../components/myfiles/FilePathPromptModal';
import { createTempFileShare, buildTempFileDownloadUrl } from '../../api/share';

type FilePrompt =
  | { kind: 'rename'; file: MyFile }
  | { kind: 'move'; file: MyFile };

import { formatDateTime } from '../../lib/formatLocale';

function formatExpiry(iso: string): string {
  return formatDateTime(iso);
}

interface MyFilesViewProps {
  onOpenTask: (sessionId: string, filePath: string) => void;
}

/** W35 — cross-session file center (G18). */
export function MyFilesView({ onOpenTask }: MyFilesViewProps) {
  const [files, setFiles] = useState<MyFile[]>([]);
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<MyFilesSort>('updatedAt');
  const [order, setOrder] = useState<MyFilesOrder>('desc');
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busyPath, setBusyPath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [filePrompt, setFilePrompt] = useState<FilePrompt | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await listMyFiles({ q: query, sort, order, favoritesOnly });
      setFiles(items);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [favoritesOnly, order, query, sort]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timer = window.setTimeout(() => setToast(null), 2500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const handleAction = async (action: MyFileAction, file: MyFile) => {
    if (action === 'openTask') {
      onOpenTask(file.sessionId, file.path);
      return;
    }
    if (action === 'download') {
      window.open(myFileDownloadUrl(file.sessionId, file.path), '_blank', 'noopener,noreferrer');
      return;
    }
    if (action === 'share') {
      setBusyPath(`${file.sessionId}:${file.path}`);
      try {
        const link = await createTempFileShare(file.sessionId, file.path, 72);
        const url = buildTempFileDownloadUrl(window.location.origin, link.token);
        await navigator.clipboard.writeText(url);
        setToast(`临时下载链接已复制（${formatExpiry(link.expiresAt)} 前有效）`);
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setBusyPath(null);
      }
      return;
    }

    setBusyPath(`${file.sessionId}:${file.path}`);
    try {
      if (action === 'rename') {
        setFilePrompt({ kind: 'rename', file });
        setBusyPath(null);
        return;
      }
      if (action === 'move') {
        setFilePrompt({ kind: 'move', file });
        setBusyPath(null);
        return;
      }
      if (action === 'favorite') {
        await favoriteMyFile(file.sessionId, file.path, true);
      } else if (action === 'unfavorite') {
        await favoriteMyFile(file.sessionId, file.path, false);
      } else if (action === 'delete') {
        if (!window.confirm(`确定删除「${file.name}」？此操作不可撤销。`)) {
          setBusyPath(null);
          return;
        }
        await deleteMyFile(file.sessionId, file.path);
      }
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyPath(null);
    }
  };

  const handlePromptConfirm = async (value: string) => {
    if (!filePrompt) {
      return;
    }
    const { kind, file } = filePrompt;
    if (kind === 'rename' && (value === file.name || !value.trim())) {
      setFilePrompt(null);
      return;
    }
    if (kind === 'move' && (value === file.path || !value.trim())) {
      setFilePrompt(null);
      return;
    }
    setBusyPath(`${file.sessionId}:${file.path}`);
    try {
      if (kind === 'rename') {
        await renameMyFile(file.sessionId, file.path, value.trim());
        setToast('已重命名');
      } else {
        await moveMyFile(file.sessionId, file.path, value.trim());
        setToast('已移动');
      }
      setFilePrompt(null);
      await load();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyPath(null);
    }
  };

  return (
    <main className="myfiles-page">
      <header className="myfiles-header">
        <div>
          <h1>文件中心</h1>
          <p className="muted">跨任务浏览工作区产物，区别于单会话右侧工件面板。</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load()} disabled={loading}>
          刷新
        </button>
      </header>

      <div className="myfiles-toolbar">
        <input
          type="search"
          className="myfiles-search"
          placeholder="搜索文件名、路径或任务标题"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <label className="myfiles-filter">
          <input
            type="checkbox"
            checked={favoritesOnly}
            onChange={(event) => setFavoritesOnly(event.target.checked)}
          />
          仅收藏
        </label>
        <div className="myfiles-toolbar-controls">
          <select
            className="myfiles-sort"
            value={sort}
            onChange={(event) => setSort(event.target.value as MyFilesSort)}
            aria-label="排序字段"
          >
            <option value="updatedAt">按时间</option>
            <option value="name">按名称</option>
          </select>
          <select
            className="myfiles-sort"
            value={order}
            onChange={(event) => setOrder(event.target.value as MyFilesOrder)}
            aria-label="排序方向"
          >
            <option value="desc">降序</option>
            <option value="asc">升序</option>
          </select>
        </div>
      </div>

      {error && <p className="myfiles-error">{error}</p>}
      {toast && <p className="myfiles-toast">{toast}</p>}

      <div className="myfiles-table-wrap">
        <table className="myfiles-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>所属任务</th>
              <th>更新时间</th>
              <th>大小</th>
              <th aria-label="操作" />
            </tr>
          </thead>
          <tbody>
            {files.map((file) => (
              <FileRow
                key={`${file.sessionId}:${file.path}`}
                file={file}
                busy={busyPath === `${file.sessionId}:${file.path}`}
                onAction={(action, item) => void handleAction(action, item)}
              />
            ))}
          </tbody>
        </table>
        {!loading && files.length === 0 && (
          <p className="myfiles-empty">暂无文件。在任务中让 Agent 生成产物后，会出现在这里。</p>
        )}
        {loading && <p className="myfiles-empty">加载中…</p>}
      </div>

      <FilePathPromptModal
        open={filePrompt?.kind === 'rename'}
        title="重命名文件"
        label="新文件名"
        initialValue={filePrompt?.kind === 'rename' ? filePrompt.file.name : ''}
        confirmLabel="重命名"
        busy={Boolean(busyPath)}
        onConfirm={(value) => void handlePromptConfirm(value)}
        onCancel={() => setFilePrompt(null)}
      />
      <FilePathPromptModal
        open={filePrompt?.kind === 'move'}
        title="移动文件"
        label="目标路径（相对工作区）"
        initialValue={filePrompt?.kind === 'move' ? filePrompt.file.path : ''}
        placeholder="例如 reports/summary.md"
        confirmLabel="移动"
        busy={Boolean(busyPath)}
        onConfirm={(value) => void handlePromptConfirm(value)}
        onCancel={() => setFilePrompt(null)}
      />
    </main>
  );
}
