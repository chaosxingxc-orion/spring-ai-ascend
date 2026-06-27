import { useCallback, useEffect, useMemo, useState } from 'react';
import { listStudioPlaybooks } from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import type { StudioPlaybookListItem } from '../../types/studio';

interface PlaybookListPanelProps {
  onOpenPlaybook: (playbookId: string) => void;
}

export function PlaybookListPanel({ onOpenPlaybook }: PlaybookListPanelProps) {
  const [rows, setRows] = useState<StudioPlaybookListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await listStudioPlaybooks());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return rows;
    }
    return rows.filter((row) => `${row.id} ${row.title} ${row.placements.join(' ')}`.toLowerCase().includes(q));
  }, [query, rows]);

  return (
    <div className="dev-studio-list-panel">
      <div className="dev-studio-toolbar">
        <input
          type="search"
          className="dev-studio-search"
          placeholder="搜索 playbook id / 标题 / placement"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button type="button" className="btn ghost sm" onClick={() => void load()}>
          刷新
        </button>
        <button type="button" className="btn primary sm" onClick={() => onOpenPlaybook('new')}>
          新建 Playbook
        </button>
      </div>

      {error && <div className="dev-studio-error">{error}</div>}
      {loading && <p className="muted">加载中…</p>}
      <div className="dev-studio-table-wrap">
        <table className="dev-studio-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>Placements</th>
              <th>来源</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr key={item.id} className="dev-studio-row-clickable" onClick={() => onOpenPlaybook(item.id)}>
                <td><code>{item.id}</code></td>
                <td>{item.title}</td>
                <td>{item.placements.join(', ') || '—'}</td>
                <td>
                  <span className={`dev-studio-badge dev-studio-badge-${item.source.toLowerCase()}`}>
                    {sourceLabel(item.source)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && <p className="muted dev-studio-empty">没有匹配的 Playbook</p>}
      </div>
      <p className="muted dev-studio-hint">编辑内置 Playbook 会写入草稿层；保存后 reload 即生效。</p>
    </div>
  );
}
