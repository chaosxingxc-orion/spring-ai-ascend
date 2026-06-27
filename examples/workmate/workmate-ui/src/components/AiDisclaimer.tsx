/** F7 — enterprise AI disclaimer footer. */
import { t } from '../lib/i18n';

export function AiDisclaimer() {
  return (
    <p className="ai-disclaimer" role="note">
      {t('chat.aiDisclaimer')}
    </p>
  );
}
