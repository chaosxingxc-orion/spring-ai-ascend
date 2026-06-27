import { useCallback, useEffect, useState } from 'react';
import { listWorkspaceEntries } from '../api/client';
import type { WorkspaceEntry } from '../types/api';

interface WorkspaceTreeProps {
  sessionId: string | null;
  refreshKey: number;
  selectedPath: string | null;
  compact?: boolean;
  onSelectFile: (path: string) => void;
}

interface TreeNodeProps {
  sessionId: string;
  entry: WorkspaceEntry;
  depth: number;
  selectedPath: string | null;
  onSelectFile: (path: string) => void;
  refreshKey: number;
  autoExpandDepth?: number;
}

function TreeNode({
  sessionId,
  entry,
  depth,
  selectedPath,
  onSelectFile,
  refreshKey,
  autoExpandDepth = 0,
}: TreeNodeProps) {
  const [expanded, setExpanded] = useState(depth < autoExpandDepth);
  const [children, setChildren] = useState<WorkspaceEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadChildren = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const items = await listWorkspaceEntries(sessionId, entry.path);
      setChildren(items);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [entry.path, sessionId]);

  useEffect(() => {
    if (entry.type === 'dir' && expanded) {
      void loadChildren();
    }
  }, [entry.type, expanded, loadChildren, refreshKey]);

  if (entry.type === 'file') {
    return (
      <li className="tree-node">
        <button
          type="button"
          className={`tree-item file${selectedPath === entry.path ? ' active' : ''}`}
          style={{ paddingLeft: `${12 + depth * 14}px` }}
          onClick={() => onSelectFile(entry.path)}
        >
          <span className="tree-icon">📄</span>
          <span className="tree-name">{entry.name}</span>
        </button>
      </li>
    );
  }

  return (
    <li className="tree-node">
      <button
        type="button"
        className="tree-item dir"
        style={{ paddingLeft: `${12 + depth * 14}px` }}
        onClick={() => setExpanded((open) => !open)}
        aria-expanded={expanded}
      >
        <span className="tree-icon">{expanded ? '📂' : '📁'}</span>
        <span className="tree-name">{entry.name}</span>
      </button>
      {expanded && (
        <ul className="tree-children">
          {loading && (
            <li className="tree-hint" style={{ paddingLeft: `${26 + depth * 14}px` }}>
              加载中…
            </li>
          )}
          {error && (
            <li className="tree-hint error" style={{ paddingLeft: `${26 + depth * 14}px` }}>
              {error}
            </li>
          )}
          {!loading &&
            children.map((child) => (
              <TreeNode
                key={child.path}
                sessionId={sessionId}
                entry={child}
                depth={depth + 1}
                selectedPath={selectedPath}
                onSelectFile={onSelectFile}
                refreshKey={refreshKey}
                autoExpandDepth={autoExpandDepth}
              />
            ))}
          {!loading && !error && children.length === 0 && (
            <li className="tree-hint muted" style={{ paddingLeft: `${26 + depth * 14}px` }}>
              空目录
            </li>
          )}
        </ul>
      )}
    </li>
  );
}

export function WorkspaceTree({
  sessionId,
  refreshKey,
  selectedPath,
  compact = false,
  onSelectFile,
}: WorkspaceTreeProps) {
  const [entries, setEntries] = useState<WorkspaceEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadRoot = useCallback(async () => {
    if (!sessionId) {
      setEntries([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const items = await listWorkspaceEntries(sessionId);
      setEntries(items);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadRoot();
  }, [loadRoot, refreshKey]);

  if (!sessionId) {
    return <p className="artifact-hint muted">选择任务后浏览工作区</p>;
  }
  if (loading) {
    return <p className="artifact-hint muted">加载中…</p>;
  }
  if (error) {
    return <p className="artifact-hint error">{error}</p>;
  }
  if (entries.length === 0) {
    return <p className="artifact-hint muted">工作区为空</p>;
  }

  const autoExpandDepth = compact ? 1 : 0;

  return (
    <ul className={`workspace-tree${compact ? ' compact' : ''}`}>
      {entries.map((entry) => (
        <TreeNode
          key={entry.path}
          sessionId={sessionId}
          entry={entry}
          depth={0}
          selectedPath={selectedPath}
          onSelectFile={onSelectFile}
          refreshKey={refreshKey}
          autoExpandDepth={autoExpandDepth}
        />
      ))}
    </ul>
  );
}
