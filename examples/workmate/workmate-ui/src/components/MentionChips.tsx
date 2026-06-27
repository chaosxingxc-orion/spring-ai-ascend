import type { MentionRef } from '../types/mention';
import { mentionIcon } from '../lib/mentionParse';

interface MentionChipsProps {
  mentions: MentionRef[];
  onRemove: (index: number) => void;
}

export function MentionChips({ mentions, onRemove }: MentionChipsProps) {
  if (mentions.length === 0) {
    return null;
  }
  return (
    <div className="mention-chips" aria-label="已选提及">
      {mentions.map((mention, index) => (
        <span key={`${mention.type}-${mention.id}-${index}`} className="mention-chip">
          <span aria-hidden>{mentionIcon(mention.type)}</span>
          <span className="mention-chip-label">{mention.label}</span>
          <button
            type="button"
            className="mention-chip-remove"
            aria-label={`移除 ${mention.label}`}
            onClick={() => onRemove(index)}
          >
            ×
          </button>
        </span>
      ))}
    </div>
  );
}
