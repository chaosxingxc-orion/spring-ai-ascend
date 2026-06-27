import { DiscoverFavoriteButton } from '../../components/DiscoverFavoriteButton';
import type { SkillInfo } from '../../types/market';
import { marketCapabilityPillLabel } from '../../lib/terminology';

interface SkillMarketCardProps {
  skill: SkillInfo;
  busy: boolean;
  showFavorite?: boolean;
  onInstall: () => void;
  onUninstall?: () => void;
  /** Horizontal featured row — same card chrome, fixed width. */
  variant?: 'grid' | 'featured';
}

export function SkillMarketCard({
  skill,
  busy,
  showFavorite = true,
  onInstall,
  onUninstall,
  variant = 'grid',
}: SkillMarketCardProps) {
  const installed = skill.installed;
  const locked = Boolean(skill.policyLocked && installed);

  return (
    <article className={`market-capability-card skill-market-card${variant === 'featured' ? ' market-capability-card--featured' : ''}`}>
      <div className="market-capability-card__actions">
        {showFavorite && variant === 'grid' && (
          <DiscoverFavoriteButton type="skill" id={skill.id} compact />
        )}
        {installed ? (
          <span className="skill-installed-badge">已安装</span>
        ) : (
          <button
            type="button"
            className="btn ghost skill-add"
            disabled={busy}
            title="安装技能"
            aria-label={`安装 ${skill.name}`}
            onClick={onInstall}
          >
            {busy ? '…' : '+'}
          </button>
        )}
      </div>
      <header className="market-capability-card__header">
        <h3 className="market-capability-card__title">{skill.name}</h3>
      </header>
      <div className="market-capability-card__body">
        <p className="market-capability-card__desc">{skill.description || '暂无描述'}</p>
        <ul className="market-meta-pills market-capability-card__pills">
          <li className="pill-skill">{marketCapabilityPillLabel('skill', skill.id)}</li>
          {skill.category && <li className="pill-category">{skill.category}</li>}
          {variant === 'grid' && skill.source && skill.source !== 'recommended' && (
            <li className="pill-source">{skill.source}</li>
          )}
        </ul>
      </div>
      <footer className="market-capability-card__footer">
        {installed && !locked && onUninstall && (
          <button type="button" className="btn secondary" disabled={busy} onClick={onUninstall}>
            卸载
          </button>
        )}
        {locked && <span className="market-capability-card__hint">内置默认 · 不可卸载</span>}
      </footer>
    </article>
  );
}
