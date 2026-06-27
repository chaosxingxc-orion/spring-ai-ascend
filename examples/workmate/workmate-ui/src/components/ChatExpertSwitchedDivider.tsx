import { t } from '../lib/i18n';

interface ChatExpertSwitchedDividerProps {
  fromExpertName?: string;
  toExpertName: string;
}

/** W53 — visual anchor when the session expert changes mid-conversation. */
export function ChatExpertSwitchedDivider({ fromExpertName, toExpertName }: ChatExpertSwitchedDividerProps) {
  const fromLabel = fromExpertName?.trim() || t('chat.expertSwitchedDefaultFrom');
  const label = t('chat.expertSwitchedLabel', { from: fromLabel, to: toExpertName });
  return (
    <div className="chat-expert-switch" role="separator" aria-label={label}>
      <span className="chat-expert-switch-line" aria-hidden />
      <div className="chat-expert-switch-body">
        <span className="chat-expert-switch-label">{label}</span>
        <span className="chat-expert-switch-hint">{t('chat.expertSwitchedHint')}</span>
      </div>
      <span className="chat-expert-switch-line" aria-hidden />
    </div>
  );
}
