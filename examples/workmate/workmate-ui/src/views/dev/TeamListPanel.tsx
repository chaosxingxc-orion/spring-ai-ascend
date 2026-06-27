import { useCallback, useEffect, useMemo, useState } from 'react';
import { listStudioExperts } from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import type { OfficeAssetSource, StudioExpertListItem } from '../../types/studio';

interface TeamListPanelProps {
  onOpenTeam: (teamId: string) => void;
}

const SOURCE_FILTERS: Array<{ value: '' | OfficeAssetSource; label: string }> = [
  { value: '', label: '全部来源' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'BUILTIN', label: '内置' },
  { value: 'MARKET', label: '市场' },
  { value: 'IMPORT', label: '导入' },
];

export function TeamListPanel({ onOpenTeam }: TeamListPanelProps) {
  const [items, setItems] = useState<StudioExpertListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [sourceFilter, setSourceFilter] = useState<'' | OfficeAssetSource>('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listStudioExperts());
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
    return items
      .filter((item) => item.summary.expertType === 'team')
      .filter((item) => {
        if (sourceFilter && item.source !== sourceFilter) {
          return false;
        }
        if (!q) {
          return true;
        }
        const haystack = [
          item.summary.id,
          item.summary.name,
          item.summary.description,
          ...(item.summary.tags ?? []),
        ]
          .join(' ')
          .toLowerCase();
        return haystack.includes(q);
      });
  }, [items, query, sourceFilter]);

  return (
    <div className="dev-studio-list-panel">
      <div className="dev-studio-toolbar">
        <input
          type="search"
          className="dev-studio-search"
          placeholder="搜索 id / 名称 / 标签"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <select
          value={sourceFilter}
          onChange={(event) => setSourceFilter(event.target.value as '' | OfficeAssetSource)}
          aria-label="来源筛选"
        >
          {SOURCE_FILTERS.map((option) => (
            <option key={option.value || 'all'} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <button type="button" className="btn ghost sm" onClick={() => void load()}>
          刷新
        </button>
        <button type="button" className="btn primary sm" onClick={() => onOpenTeam('new')}>
          新建专家团
        </button>
      </div>

      {error && <div className="dev-studio-error">{error}</div>}
      {loading && <p className="muted">加载中…</p>}

      <div className="dev-studio-table-wrap">
        <table className="dev-studio-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>名称</th>
              <th>拓扑</th>
              <th>Runtime</th>
              <th>来源</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr
                key={item.summary.id}
                className="dev-studio-row-clickable"
                onClick={() => onOpenTeam(item.summary.id)}
              >
                <td><code>{item.summary.id}</code></td>
                <td>{item.summary.name}</td>
                <td>{item.summary.coordination?.pattern ?? '—'}</td>
                <td>{item.summary.teamRuntime ?? '—'}</td>
                <td>
                  <span className={`dev-studio-badge dev-studio-badge-${item.source.toLowerCase()}`}>
                    {sourceLabel(item.source)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && <p className="muted dev-studio-empty">没有匹配的专家团</p>}
      </div>
      <p className="muted dev-studio-hint">点击行进入编排器；新建会生成 ≥2 成员的 orchestrator 草稿模板。</p>
    </div>
  );
}
