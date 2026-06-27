import type { TeamBusEntry } from './teamStatus';

/** EventLanes 泳道条目定位键（topic + 泳道内 index）。 */
export interface BusLaneHighlight {
  topic: string;
  index: number;
}

export function busLaneEntryKey(topic: string, index: number): string {
  return `${topic}#${index}`;
}

function normalizePreview(text?: string): string {
  if (!text) {
    return '';
  }
  return text.trim().replace(/…+$/u, '').trim();
}

export function previewMatches(cardPreview?: string, entryPreview?: string): boolean {
  const a = normalizePreview(cardPreview);
  const b = normalizePreview(entryPreview);
  if (!a && !b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return a === b || a.startsWith(b) || b.startsWith(a);
}

/** 从工具卡片 topic/preview 定位泳道中最可能的 mid-run 条目（自后向前）。 */
export function findBusLaneHighlight(
  lanes: Record<string, TeamBusEntry[]>,
  topic: string | undefined,
  preview?: string,
): BusLaneHighlight | null {
  if (!topic) {
    return null;
  }
  const entries = lanes[topic];
  if (!entries?.length) {
    return null;
  }

  for (let i = entries.length - 1; i >= 0; i -= 1) {
    const entry = entries[i];
    if (previewMatches(preview, entry.preview)) {
      return { topic, index: i };
    }
  }

  for (let i = entries.length - 1; i >= 0; i -= 1) {
    if (entries[i].publishSource === 'mid-run') {
      return { topic, index: i };
    }
  }

  return { topic, index: entries.length - 1 };
}

export function isSameBusLaneHighlight(
  a: BusLaneHighlight | null | undefined,
  b: BusLaneHighlight | null | undefined,
): boolean {
  return Boolean(a && b && a.topic === b.topic && a.index === b.index);
}
