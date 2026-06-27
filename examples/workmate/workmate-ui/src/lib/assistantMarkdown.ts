/** Normalize model markdown for chat display (generic, not expert-specific). */

/** Split a heading marker glued to the end of a previous line onto its own line. */
function splitGluedHeadings(text: string): string {
  return text.replace(/([^\n])(#{2,3}\s*)/g, '$1\n$2');
}

function normalizeHeadingSpacing(text: string): string {
  return text.replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2');
}

/** Lines that are only hash marks with no title (broken ### splits). */
function removeOrphanHashLines(text: string): string {
  return text.replace(/^#{1,6}\s*$/gm, '');
}

/** Drop streaming replays where the model restarts mid-bubble with duplicate paragraphs. */
function dedupeStreamingReplay(text: string): string {
  const trimmed = text.trim();
  if (trimmed.length < 80) {
    return text;
  }

  const lines = trimmed.split('\n').map((line) => line.trim()).filter(Boolean);
  const candidates = [
    ...lines.slice(0, 8),
    ...lines.filter((line) => line.startsWith('|')),
  ];

  for (const line of candidates) {
    const minLen = line.startsWith('|') ? 8 : 8;
    if (line.length < minLen) {
      continue;
    }
    const firstAt = trimmed.indexOf(line);
    if (firstAt < 0) {
      continue;
    }
    const secondAt = trimmed.indexOf(line, firstAt + line.length);
    if (secondAt > trimmed.length * 0.3) {
      return trimmed.slice(0, secondAt).trim();
    }
  }

  return text;
}

/**
 * Lightweight, non-destructive cleanup only.
 *
 * We deliberately avoid restructuring or deleting the model's output (no section
 * de-duplication, no heading-level demotion, no report field / TL;DR rewriting)
 * so the conversation renders faithfully. The only fixes here repair broken
 * markup that would otherwise leak literal `#` characters into the rendered text,
 * and trim obvious streaming replays that restart the same paragraph mid-bubble.
 */
export function prepareAssistantMarkdown(source: string): string {
  let text = source.replace(/\r\n/g, '\n');
  text = dedupeStreamingReplay(text);
  text = splitGluedHeadings(text);
  text = normalizeHeadingSpacing(text);
  text = removeOrphanHashLines(text);
  return text;
}
