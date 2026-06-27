import type { ChatItem } from '../types/events';

export interface SearchHit {
  itemId: string;
}

function searchableText(item: ChatItem): string {
  switch (item.kind) {
    case 'user':
    case 'assistant':
    case 'system':
      return item.text;
    case 'plan':
      return [item.title, ...item.steps.map((step) => step.title)].filter(Boolean).join(' ');
    case 'approval':
      return [item.tool, item.summary, item.reason].join(' ');
    case 'question':
      return [item.question, ...(item.selections ?? []), item.answerText ?? ''].join(' ');
    case 'tool':
      return item.toolName;
    case 'tool-group':
      return item.tools.map((tool) => tool.toolName).join(' ');
    default:
      return '';
  }
}

export function findSearchHits(items: ChatItem[], query: string): SearchHit[] {
  const needle = query.trim().toLowerCase();
  if (!needle) {
    return [];
  }
  return items
    .filter((item) => searchableText(item).toLowerCase().includes(needle))
    .map((item) => ({ itemId: item.id }));
}
