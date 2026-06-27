import type { CSSProperties } from 'react';
import { useEffect, useState } from 'react';
import {
  launchDiscoverResource,
  listDiscoverResources,
  toggleDiscoverFavorite,
  type DiscoverResource,
} from '../../api/discover';
import { listPlaybooks } from '../../api/welcome';
import { DiscoverFavoriteButton } from '../../components/DiscoverFavoriteButton';
import type { PlaybookCard } from '../../types/welcome';

interface DiscoverSectionProps {
  onLaunch: (payload: { initPrompt: string; expertId?: string; title: string }) => void;
  onPlaybookSelect?: (playbook: PlaybookCard) => void;
}

/** W47-C1 — Playbook 横滚 +「用过」「收藏」Tab */
export function DiscoverSection({ onLaunch, onPlaybookSelect }: DiscoverSectionProps) {
  const [resources, setResources] = useState<DiscoverResource[]>([]);
  const [playbooks, setPlaybooks] = useState<PlaybookCard[]>([]);
  const [favoritesOnly, setFavoritesOnly] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyKey, setBusyKey] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    void Promise.all([
      listDiscoverResources(favoritesOnly),
      listPlaybooks('market-featured'),
    ])
      .then(([items, playbookItems]) => {
        setResources(items);
        setPlaybooks(playbookItems);
      })
      .catch(() => {
        setResources([]);
        setPlaybooks([]);
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [favoritesOnly]);

  const handleFavorite = async (resource: DiscoverResource) => {
    const key = `${resource.type}:${resource.id}`;
    setBusyKey(key);
    try {
      const updated = await toggleDiscoverFavorite(resource.type, resource.id, !resource.favorite);
      const apply = (list: DiscoverResource[]) =>
        list.map((item) => (item.type === updated.type && item.id === updated.id ? updated : item));
      setResources((prev) => {
        const next = apply(prev);
        return favoritesOnly && !updated.favorite
          ? next.filter((item) => item.favorite)
          : next;
      });
    } finally {
      setBusyKey(null);
    }
  };

  const handleLaunch = async (resource: DiscoverResource) => {
    const key = `${resource.type}:${resource.id}`;
    setBusyKey(key);
    try {
      const launch = await launchDiscoverResource(resource.type, resource.id);
      onLaunch({
        initPrompt: launch.initPrompt,
        expertId: launch.expertId,
        title: launch.title,
      });
    } finally {
      setBusyKey(null);
    }
  };

  const handlePlaybook = (playbook: PlaybookCard) => {
    if (onPlaybookSelect) {
      onPlaybookSelect(playbook);
      return;
    }
    onLaunch({
      initPrompt: playbook.initPrompt,
      expertId: playbook.expertId,
      title: playbook.title,
    });
  };

  if (loading) {
    return null;
  }

  return (
    <section className="discover-section">
      <header className="discover-section-header">
        <h2>发现</h2>
        <div className="market-subtabs">
          <button
            type="button"
            className={`market-pill${!favoritesOnly ? ' active' : ''}`}
            onClick={() => setFavoritesOnly(false)}
          >
            用过
          </button>
          <button
            type="button"
            className={`market-pill${favoritesOnly ? ' active' : ''}`}
            onClick={() => setFavoritesOnly(true)}
          >
            收藏
          </button>
        </div>
      </header>

      {!favoritesOnly && playbooks.length > 0 && (
        <section className="featured-section discover-playbooks" aria-label="精选 Playbook">
          <div className="featured-section-header">
            <h3 className="market-section-title">精选 Playbook</h3>
          </div>
          <div className="featured-scroll">
            {playbooks.map((playbook) => (
              <div key={playbook.id} className="playbook-card-wrap">
                <button
                  type="button"
                  className="playbook-card"
                  style={
                    playbook.accent
                      ? ({ '--playbook-accent': playbook.accent } as CSSProperties)
                      : undefined
                  }
                  onClick={() => handlePlaybook(playbook)}
                >
                  <span className="playbook-card-title">{playbook.title}</span>
                  {playbook.description && (
                    <span className="playbook-card-desc">{playbook.description}</span>
                  )}
                </button>
                <DiscoverFavoriteButton type="playbook" id={playbook.id} compact />
              </div>
            ))}
          </div>
        </section>
      )}

      {resources.length === 0 ? (
        <p className="market-empty">
          {favoritesOnly
            ? '暂无收藏 — 在 Playbook 或资源卡片上点击收藏。'
            : '暂无使用记录 — 启动 Playbook 或市场资源后将出现在这里。'}
        </p>
      ) : (
        <div className="discover-grid">
          {resources.map((resource) => {
            const key = `${resource.type}:${resource.id}`;
            return (
              <article key={key} className="discover-card market-capability-card">
                <header>
                  <h3>{resource.title}</h3>
                  <span className="discover-type">{resource.type}</span>
                </header>
                {resource.subtitle && <p>{resource.subtitle}</p>}
                {resource.lastUsedAt && (
                  <p className="discover-last-used muted">最近使用</p>
                )}
                <div className="discover-card-actions">
                  <button
                    type="button"
                    className="btn ghost compact"
                    disabled={busyKey === key}
                    onClick={() => void handleFavorite(resource)}
                  >
                    {resource.favorite ? '取消收藏' : '收藏'}
                  </button>
                  <button
                    type="button"
                    className="btn primary compact"
                    disabled={busyKey === key}
                    onClick={() => void handleLaunch(resource)}
                  >
                    启动
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}
