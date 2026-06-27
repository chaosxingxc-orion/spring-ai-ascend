interface PlainTextContentProps {
  source: string;
  className?: string;
}

/** Member trace text — preserve line breaks, no markdown heuristics. */
export function PlainTextContent({ source, className }: PlainTextContentProps) {
  return <div className={className ? `${className} plain-text-content` : 'plain-text-content'}>{source}</div>;
}
