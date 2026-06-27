import type { MentionRef } from '../types/mention';
import { mentionGroupLabel, mentionIcon } from '../lib/mentionParse';

interface MentionContextCardsProps {
  mentions?: MentionRef[];
}

/** 用户消息顶部的上下文锚点卡片 */
export function MentionContextCards({ mentions }: MentionContextCardsProps) {
  if (!mentions?.length) {
    return null;
  }
  return (
    <div className="mention-context-cards">
      {mentions.map((mention, index) => (
        <div key={`${mention.type}-${mention.id}-${index}`} className="mention-context-card">
          <span className="mention-context-icon" aria-hidden>
            {mentionIcon(mention.type)}
          </span>
          <span className="mention-context-meta">
            <span className="mention-context-label">{mention.label}</span>
            <span className="mention-context-type">{mentionGroupLabel(mention.type)}</span>
          </span>
        </div>
      ))}
    </div>
  );
}
