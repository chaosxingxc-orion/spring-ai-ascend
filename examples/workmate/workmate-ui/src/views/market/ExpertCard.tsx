import type { Expert } from '../../types/api';
import { DiscoverFavoriteButton } from '../../components/DiscoverFavoriteButton';
import { resolveExpertDisplayName, resolveExpertProfession } from '../../lib/teamUiLabels';
import { expertIsBeta } from '../../lib/expertMarketFilter';
import {
  expertRuntimeType,
  formatConnectorLabel,
  isTeamExpertType,
  summonActionLabel,
  TERM,
} from '../../lib/terminology';

interface ExpertCardProps {
  expert: Expert;
  onSelect: (expert: Expert) => void;
  onSummon: (expert: Expert) => void;
}

const MAX_VISIBLE_TAGS = 4;
const MAX_VISIBLE_MCP = 3;

export function ExpertCard({ expert, onSelect, onSummon }: ExpertCardProps) {
  const isTeam = isTeamExpertType(expert.expertType);
  const displayName = resolveExpertDisplayName(expert);
  const profession = resolveExpertProfession(expert);
  const isBeta = expertIsBeta(expert);
  const visibleTags = expert.tags.slice(0, MAX_VISIBLE_TAGS);
  const overflowTags = Math.max(0, expert.tags.length - MAX_VISIBLE_TAGS);
  const visibleMcp = expert.skillCompatibility.slice(0, MAX_VISIBLE_MCP);
  const overflowMcp = Math.max(0, expert.skillCompatibility.length - MAX_VISIBLE_MCP);

  return (
    <article className="expert-card market-expert-card">
      <div className="expert-card-favorite-anchor">
        <DiscoverFavoriteButton type="expert" id={expert.id} compact />
      </div>
      <button type="button" className="expert-card-main" onClick={() => onSelect(expert)}>
        <header className="expert-card-header">
          <div className="expert-card-avatar" aria-hidden>
            {isTeam ? '团' : displayName.slice(0, 1)}
          </div>
          <div className="expert-card-titles">
            <div className="expert-card-name-row">
              <h3 className="expert-card-name">{displayName}</h3>
              {isBeta && <span className="expert-card-beta">Beta</span>}
              {isTeam && <span className="expert-card-badge team">专家团</span>}
            </div>
            {profession && <span className="expert-card-profession">{profession}</span>}
            <span className="expert-card-id">
              <span className="expert-card-id-text">{expert.id}</span>
              <span className="expert-card-runtime">{expertRuntimeType(expert.expertType)}</span>
            </span>
          </div>
        </header>
        <div className="expert-card-content">
          <p className="expert-card-desc">{expert.description || '暂无描述'}</p>
          {(visibleTags.length > 0 || overflowTags > 0) && (
            <ul className="expert-card-tags">
              {visibleTags.map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
              {overflowTags > 0 && <li className="tag-overflow">+{overflowTags}</li>}
            </ul>
          )}
          {(visibleMcp.length > 0 || overflowMcp > 0) && (
            <ul className="expert-card-tags expert-card-mcp-tags" aria-label={`推荐${TERM.connector}`}>
              {visibleMcp.map((mcpId) => (
                <li key={mcpId} className="tag-connector">
                  {TERM.connector} · {formatConnectorLabel(mcpId)}
                </li>
              ))}
              {overflowMcp > 0 && (
                <li className="tag-connector tag-overflow" title={`还有 ${overflowMcp} 个${TERM.connector}`}>
                  +{overflowMcp}
                </li>
              )}
            </ul>
          )}
        </div>
      </button>
      <footer className="expert-card-footer">
        <button
          type="button"
          className="btn primary"
          onClick={(event) => {
            event.stopPropagation();
            onSummon(expert);
          }}
        >
          {summonActionLabel(expert.expertType)}
        </button>
      </footer>
    </article>
  );
}
