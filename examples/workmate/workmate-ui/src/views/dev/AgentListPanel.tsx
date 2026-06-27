import { useCallback, useEffect, useMemo, useState } from 'react';
import { listStudioExperts } from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import type { OfficeAssetSource, StudioExpertListItem } from '../../types/studio';
import { ExpertImportModal } from '../market/ExpertImportModal';

interface AgentListPanelProps {
  onOpenExpert: (expertId: string) => void;
  onOpenTeam?: (teamId: string) => void;
  onImported?: (id: string, expertType?: string) => void;
}

const SOURCE_FILTERS: Array<{ value: '' | OfficeAssetSource; label: string }> = [
  { value: '', label: '全部来源' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'BUILTIN', label: '内置' },
  { value: 'MARKET', label: '市场' },
  { value: 'IMPORT', label: '导入' },
];

export function AgentListPanel({ onOpenExpert, onOpenTeam, onImported }: AgentListPanelProps) {
  const [items, setItems] = useState<StudioExpertListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [sourceFilter, setSourceFilter] = useState<'' | OfficeAssetSource>('');
  const [typeFilter, setTypeFilter] = useState<'all' | 'agent' | 'team'>('all');
  const [importOpen, setImportOpen] = useState(false);

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
    return items.filter((item) => {
      if (sourceFilter && item.source !== sourceFilter) {
        return false;
      }
      if (typeFilter !== 'all' && item.summary.expertType !== typeFilter) {
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
  }, [items, query, sourceFilter, typeFilter]);

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
        <select
          value={typeFilter}
          onChange={(event) => setTypeFilter(event.target.value as 'all' | 'agent' | 'team')}
          aria-label="类型筛选"
        >
          <option value="all">全部类型</option>
          <option value="agent">单专家</option>
          <option value="team">专家团</option>
        </select>
        <button type="button" className="btn ghost sm" onClick={() => void load()}>
          刷新
        </button>
        <button type="button" className="btn secondary sm" onClick={() => setImportOpen(true)}>
          导入
        </button>
        <button type="button" className="btn primary sm" onClick={() => onOpenExpert('new')}>
          新建专家
        </button>
      </div>

      <ExpertImportModal
        open={importOpen}
        mode="studio"
        onClose={() => setImportOpen(false)}
        onImported={(result) => {
          void load();
          onImported?.(result.id, result.expertType);
        }}
      />

      {error && <div className="dev-studio-error">{error}</div>}
      {loading && <p className="muted">加载中…</p>}

      <div className="dev-studio-table-wrap">
        <table className="dev-studio-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>名称</th>
              <th>类型</th>
              <th>来源</th>
              <th>分类</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((item) => (
              <tr
                key={item.summary.id}
                className="dev-studio-row-clickable"
                onClick={() =>
                  item.summary.expertType === 'team' && onOpenTeam
                    ? onOpenTeam(item.summary.id)
                    : onOpenExpert(item.summary.id)
                }
              >
                <td><code>{item.summary.id}</code></td>
                <td>{item.summary.name}</td>
                <td>{item.summary.expertType}</td>
                <td>
                  <span className={`dev-studio-badge dev-studio-badge-${item.source.toLowerCase()}`}>
                    {sourceLabel(item.source)}
                  </span>
                </td>
                <td>{item.summary.category ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && <p className="muted dev-studio-empty">没有匹配的专家</p>}
      </div>
    </div>
  );
}
