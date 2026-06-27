import type { Expert } from '../../types/api';
import { expertIsBeta } from '../../lib/expertMarketFilter';
import { resolveExpertDisplayName, resolveExpertProfession } from '../../lib/teamUiLabels';
import {
  formatConnectorLabel,
  expertKindLabel,
  expertRuntimeType,
  isTeamExpertType,
  summonActionLabel,
  TERM,
} from '../../lib/terminology';

interface ExpertDetailDrawerProps {
  expert: Expert | null;
  onClose: () => void;
  onSummon: (expert: Expert) => void;
}

export function ExpertDetailDrawer({ expert, onClose, onSummon }: ExpertDetailDrawerProps) {
  if (!expert) {
    return null;
  }

  const isTeam = isTeamExpertType(expert.expertType);
  const displayName = resolveExpertDisplayName(expert);
  const profession = resolveExpertProfession(expert);
  const isBeta = expertIsBeta(expert);

  return (
    <div className="drawer-backdrop" role="presentation" onClick={onClose}>
      <aside
        className="expert-drawer"
        role="dialog"
        aria-labelledby="expert-drawer-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="expert-drawer-header">
          <div className="expert-drawer-avatar">{isTeam ? '团' : displayName.slice(0, 1)}</div>
          <div>
            <h2 id="expert-drawer-title">{displayName}</h2>
            {profession && <p className="expert-drawer-profession">{profession}</p>}
            <p className="expert-drawer-id">
              {expert.id}
              <span className="expert-card-runtime">{expertRuntimeType(expert.expertType)}</span>
            </p>
            <span className="expert-drawer-badges">
              {isBeta && <span className="expert-card-beta">Beta</span>}
              <span className={`expert-card-badge${isTeam ? ' team' : ''}`}>
                {expertKindLabel(expert.expertType)}
              </span>
            </span>
          </div>
          <button type="button" className="btn ghost drawer-close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>
        <div className="expert-drawer-body">
          <p>{expert.description || '暂无描述'}</p>
          {expert.defaultInitPrompt && (
            <details className="expert-drawer-prompt">
              <summary>默认开场 prompt</summary>
              <pre>{expert.defaultInitPrompt.trim()}</pre>
            </details>
          )}
          {expert.tags.length > 0 && (
            <ul className="expert-card-tags">
              {expert.tags.map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
          {expert.skillCompatibility.length > 0 && (
            <div className="expert-drawer-mcp">
              <span className="expert-drawer-mcp-label">推荐{TERM.connector}</span>
              <ul className="expert-card-tags expert-card-mcp-tags">
                {expert.skillCompatibility.map((mcpId) => (
                  <li key={mcpId} className="tag-connector">
                    {TERM.connector} · {formatConnectorLabel(mcpId)}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
        <footer className="expert-drawer-footer">
          <button type="button" className="btn primary" onClick={() => onSummon(expert)}>
            {summonActionLabel(expert.expertType)}
          </button>
        </footer>
      </aside>
    </div>
  );
}
