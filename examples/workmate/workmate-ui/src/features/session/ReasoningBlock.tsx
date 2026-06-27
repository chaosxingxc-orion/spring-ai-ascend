import { useState } from 'react';

interface ReasoningBlockProps {
  text: string;
  streaming?: boolean;
}

/** W37-B1 — collapsible model reasoning, separate from tool trace. */
export function ReasoningBlock({ text, streaming }: ReasoningBlockProps) {
  const [expanded, setExpanded] = useState(false);
  const trimmed = text.trim();
  if (!trimmed && !streaming) {
    return null;
  }

  return (
    <div className="reasoning-block">
      <button
        type="button"
        className="reasoning-block-header"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        <span className="reasoning-block-title">思考过程</span>
        <span className="deep-thinking-chevron" aria-hidden>{expanded ? '▾' : '▸'}</span>
      </button>
      {expanded && (
        <div className="reasoning-block-body muted">
          {trimmed || (streaming ? '思考中…' : '')}
        </div>
      )}
      {!expanded && streaming && !trimmed && (
        <div className="reasoning-block-collapsed-hint muted">思考中…</div>
      )}
    </div>
  );
}
