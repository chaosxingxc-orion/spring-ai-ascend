import { useCallback, useEffect, useMemo, useState } from 'react';
import { listStudioSkills } from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import type { StudioSkillListItem } from '../../types/studio';
import { SkillUploadModal } from '../market/SkillUploadModal';
import { DevStudioListPagination } from './DevStudioListPagination';

interface SkillListPanelProps {
  onOpenSkill: (skillId: string) => void;
  onUploaded?: (skillId: string) => void;
}

const DEFAULT_PAGE_SIZE = 20;

export function SkillListPanel({ onOpenSkill, onUploaded }: SkillListPanelProps) {
  const [items, setItems] = useState<StudioSkillListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [uploadOpen, setUploadOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setItems(await listStudioSkills());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setPage(1);
  }, [query, pageSize]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) {
      return items;
    }
    return items.filter((item) => {
      const haystack = [item.summary.id, item.summary.name, item.summary.description, ...(item.summary.tags ?? [])]
        .join(' ')
        .toLowerCase();
      return haystack.includes(q);
    });
  }, [items, query]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const safePage = Math.min(Math.max(1, page), totalPages);

  const pageItems = useMemo(() => {
    const start = (safePage - 1) * pageSize;
    return filtered.slice(start, start + pageSize);
  }, [filtered, pageSize, safePage]);

  return (
    <div className="dev-studio-list-panel">
      <div className="dev-studio-toolbar">
        <input
          type="search"
          className="dev-studio-search"
          placeholder="搜索技能 id / 名称 / 标签"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <button type="button" className="btn ghost sm" onClick={() => void load()}>
          刷新
        </button>
        <button type="button" className="btn secondary sm" onClick={() => setUploadOpen(true)}>
          上传
        </button>
        <button type="button" className="btn primary sm" onClick={() => onOpenSkill('new')}>
          新建技能
        </button>
      </div>

      <SkillUploadModal
        open={uploadOpen}
        mode="studio"
        onClose={() => setUploadOpen(false)}
        onUploaded={(skill) => {
          void load();
          onUploaded?.(skill.id);
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
              <th>分类</th>
              <th>来源</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map((item) => (
              <tr key={item.summary.id} className="dev-studio-row-clickable" onClick={() => onOpenSkill(item.summary.id)}>
                <td><code>{item.summary.id}</code></td>
                <td>{item.summary.name}</td>
                <td>{item.summary.category ?? '—'}</td>
                <td>
                  <span className={`dev-studio-badge dev-studio-badge-${item.source.toLowerCase()}`}>
                    {sourceLabel(item.source)}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!loading && filtered.length === 0 && <p className="muted dev-studio-empty">没有匹配的技能</p>}
      </div>
      {!loading && filtered.length > 0 && (
        <DevStudioListPagination
          page={safePage}
          pageSize={pageSize}
          totalItems={filtered.length}
          onPageChange={setPage}
          onPageSizeChange={setPageSize}
        />
      )}
      <p className="muted dev-studio-hint">新建/编辑走 Studio 草稿层；保存后 reload 即生效。</p>
    </div>
  );
}
