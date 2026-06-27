import { useMemo } from 'react';
import type { Expert } from '../../types/api';
import type { PlaybookCard, WelcomeSection } from '../../types/welcome';
import { TERM } from '../../lib/terminology';
import {
  EXPERT_MARKET_CATEGORIES,
  expertMarketCategoryLabel,
  expertMarketKindLabel,
  expertMatchesCategory,
  expertMatchesKind,
  type ExpertMarketKind,
} from '../../lib/expertMarketFilter';
import { ExpertCard } from './ExpertCard';
import { FeaturedSection } from './FeaturedSection';

export type ExpertSort = 'popular' | 'newest';

interface ExpertMarketTabProps {
  experts: Expert[];
  loaded?: boolean;
  query: string;
  category: string;
  kind: ExpertMarketKind;
  sort: ExpertSort;
  featuredSection: WelcomeSection;
  onCategoryChange: (category: string) => void;
  onKindChange: (kind: ExpertMarketKind) => void;
  onSortChange: (sort: ExpertSort) => void;
  onSelectExpert: (expert: Expert) => void;
  onSummonExpert: (expert: Expert) => void;
  onPlaybookSelect: (playbook: PlaybookCard) => void;
  onOpenImport?: () => void;
  searchPlaceholder?: string;
  onQueryChange: (query: string) => void;
}

export function ExpertMarketTab({
  experts,
  loaded = true,
  query,
  category,
  kind,
  sort,
  featuredSection,
  onCategoryChange,
  onKindChange,
  onSortChange,
  onSelectExpert,
  onSummonExpert,
  onPlaybookSelect,
  onOpenImport,
  searchPlaceholder,
  onQueryChange,
}: ExpertMarketTabProps) {
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let list = experts.filter((expert) => {
      if (!expertMatchesCategory(expert, category)) {
        return false;
      }
      if (!expertMatchesKind(expert, kind)) {
        return false;
      }
      if (!q) {
        return true;
      }
      return (
        expert.name.toLowerCase().includes(q) ||
        expert.id.toLowerCase().includes(q) ||
        expert.description.toLowerCase().includes(q)
      );
    });
    if (sort === 'newest') {
      list = [...list].reverse();
    }
    return list;
  }, [experts, query, category, kind, sort]);

  const agents = filtered.filter((e) => e.expertType !== 'team');
  const teams = filtered.filter((e) => e.expertType === 'team');
  const showAgents = kind !== 'team';
  const showTeams = kind !== 'agent';

  const kindTabs: ExpertMarketKind[] = ['all', 'agent', 'team'];

  return (
    <div className="market-tab-body">
      <div className="market-toolbar market-toolbar-row">
        <input
          type="search"
          className="market-search"
          placeholder={searchPlaceholder ?? '搜索专家、团队或领域关键词'}
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
        {onOpenImport && (
          <button type="button" className="btn secondary" onClick={onOpenImport}>
            导入专家
          </button>
        )}
      </div>

      <FeaturedSection section={featuredSection} onSelect={onPlaybookSelect} />

      <div className="market-toolbar-block">
        <div className="market-subtabs expert-kind-subtabs" role="tablist" aria-label="专家类型">
          {kindTabs.map((item) => (
            <button
              key={item}
              type="button"
              role="tab"
              aria-selected={kind === item}
              className={`market-pill${kind === item ? ' active' : ''}`}
              onClick={() => onKindChange(item)}
            >
              {expertMarketKindLabel(item)}
            </button>
          ))}
        </div>

        <div className="market-filter-row">
          <div className="market-category-row">
            {EXPERT_MARKET_CATEGORIES.map((cat) => (
              <button
                key={cat.id}
                type="button"
                className={`market-pill${category === cat.id ? ' active' : ''}`}
                onClick={() => onCategoryChange(cat.id)}
              >
                {expertMarketCategoryLabel(cat.id)}
              </button>
            ))}
          </div>
          <div className="market-sort-toggle" role="group" aria-label="排序">
            <button
              type="button"
              className={`market-sort-btn${sort === 'popular' ? ' active' : ''}`}
              onClick={() => onSortChange('popular')}
            >
              最热
            </button>
            <button
              type="button"
              className={`market-sort-btn${sort === 'newest' ? ' active' : ''}`}
              onClick={() => onSortChange('newest')}
            >
              最新
            </button>
          </div>
        </div>
      </div>

      {!loaded && <p className="market-hint">加载中…</p>}

      {loaded && showAgents && agents.length > 0 && (
        <section className="market-section">
          <h2 className="market-section-title">
            {TERM.expert}
            <span className="market-section-runtime">{TERM.runtimeAgent}</span>
          </h2>
          <div className="expert-grid">
            {agents.map((expert) => (
              <ExpertCard
                key={expert.id}
                expert={expert}
                onSelect={onSelectExpert}
                onSummon={onSummonExpert}
              />
            ))}
          </div>
        </section>
      )}

      {loaded && showTeams && teams.length > 0 && (
        <section className="market-section">
          <h2 className="market-section-title">
            {TERM.expertTeam}
            <span className="market-section-runtime">{TERM.runtimeMultiAgent}</span>
          </h2>
          <div className="expert-grid">
            {teams.map((expert) => (
              <ExpertCard
                key={expert.id}
                expert={expert}
                onSelect={onSelectExpert}
                onSummon={onSummonExpert}
              />
            ))}
          </div>
        </section>
      )}

      {loaded && filtered.length === 0 && (
        <p className="market-empty">
          没有匹配的{kind === 'team' ? TERM.expertTeam : kind === 'agent' ? TERM.expert : '专家或专家团'}。请调整搜索或筛选。
        </p>
      )}
    </div>
  );
}
