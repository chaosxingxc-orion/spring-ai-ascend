import { t } from '../lib/i18n';

/** F1 — visual separator between conversation turns. */
export function ChatCheckpointDivider() {
  return (
    <div className="chat-checkpoint" role="separator" aria-label={t('chat.checkpointLabel')}>
      <span className="chat-checkpoint-line" aria-hidden />
      <span className="chat-checkpoint-label">{t('chat.checkpointLabel')}</span>
      <span className="chat-checkpoint-line" aria-hidden />
    </div>
  );
}
