import type { SessionLimits } from '../types/api';
import { isAtSessionLimit, isNearSessionLimit, sessionsOverLimit } from '../lib/sessionLimits';
import { t } from '../lib/i18n';

interface SessionLimitBannerProps {
  limits: SessionLimits;
  autoArchiveEnabled: boolean;
  onShowHelp: () => void;
}

/** F6 — sidebar hint when approaching or at the active session cap (hidden when auto-archive handles it). */
export function SessionLimitBanner({
  limits,
  autoArchiveEnabled,
  onShowHelp,
}: SessionLimitBannerProps) {
  if (autoArchiveEnabled) {
    return null;
  }

  const { activeCount, maxActive } = limits;
  if (!isNearSessionLimit(activeCount, maxActive)) {
    return null;
  }

  const atLimit = isAtSessionLimit(activeCount, maxActive);
  const over = sessionsOverLimit(activeCount, maxActive);

  return (
    <div
      className={`session-limit-banner${atLimit && !autoArchiveEnabled ? ' session-limit-banner-critical' : ''}`}
      role="status"
    >
      <p className="session-limit-banner-text">
        {atLimit
          ? autoArchiveEnabled
            ? t('session.limitBannerAuto', { active: activeCount, max: maxActive })
            : t('session.limitBannerAt', { active: activeCount, max: maxActive, over })
          : t('session.limitBannerNear', { active: activeCount, max: maxActive })}
      </p>
      {!autoArchiveEnabled && (
        <button type="button" className="session-limit-banner-action" onClick={onShowHelp}>
          {t('session.limitBannerHelp')}
        </button>
      )}
    </div>
  );
}
