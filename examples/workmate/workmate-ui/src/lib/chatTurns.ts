import type { ChatItem } from '../types/events';
import { classifyTool } from './toolKind';

/** Items belonging to the latest user turn (after last user message). */
export function lastTurnItems(items: ChatItem[]): ChatItem[] {
  let lastUserIndex = -1;
  for (let index = items.length - 1; index >= 0; index -= 1) {
    if (items[index]?.kind === 'user') {
      lastUserIndex = index;
      break;
    }
  }
  return lastUserIndex >= 0 ? items.slice(lastUserIndex + 1) : items;
}

export function countTurnArtifacts(items: ChatItem[]): number {
  return lastTurnItems(items).filter((item) => item.kind === 'artifact-cta').length;
}

export function countTurnChanges(items: ChatItem[]): number {
  return lastTurnItems(items).filter(
    (item) =>
      item.kind === 'tool'
      && item.status === 'success'
      && classifyTool(item.toolName, item.args) === 'write',
  ).length;
}

export function firstTurnHtmlPreviewPath(items: ChatItem[]): string | undefined {
  const hit = lastTurnItems(items).find(
    (item) =>
      item.kind === 'artifact-cta'
      && (item.mime.includes('html') || item.path.endsWith('.html')),
  );
  return hit && hit.kind === 'artifact-cta' ? hit.path : undefined;
}

/** Map user message id → 0-based turn index among user messages. */
export function userTurnIndexById(items: ChatItem[]): Map<string, number> {
  const map = new Map<string, number>();
  let turn = 0;
  for (const item of items) {
    if (item.kind === 'user') {
      map.set(item.id, turn);
      turn += 1;
    }
  }
  return map;
}

/** F1 checkpoint — show divider before 2nd+ user turns. */
export function isFollowUpUserTurn(turnIndex: number): boolean {
  return turnIndex >= 1;
}

/** Leader work in the main timeline (excludes member-surface items). */
export function isLeaderScopedItem(item: ChatItem): boolean {
  if (item.memberId) {
    return false;
  }
  return item.kind === 'reasoning'
    || item.kind === 'assistant'
    || item.kind === 'tool'
    || item.kind === 'tool-group';
}

export interface LeaderTurnView {
  turnKey: string;
  reasoningText: string;
  leaderReasoningItemIds: ReadonlySet<string>;
  lastLeaderAssistantId?: string;
  leaderScopedIds: ReadonlySet<string>;
  firstLeaderItemId?: string;
  isLatestTurn: boolean;
}

/** Per user-turn leader segments for「深度思考」merge + turn collapse. */
export function buildLeaderTurnViews(items: ChatItem[]): LeaderTurnView[] {
  const views: LeaderTurnView[] = [];
  let sliceStart = 0;
  let turnKey = '__initial__';

  for (let index = 0; index <= items.length; index += 1) {
    const atEnd = index === items.length;
    const atUser = !atEnd && items[index].kind === 'user';
    if (!atEnd && !atUser) {
      continue;
    }

    const slice = items.slice(sliceStart, index);
    const leaderItems = slice.filter(isLeaderScopedItem);
    if (leaderItems.length > 0) {
      const reasoningParts: string[] = [];
      const leaderReasoningItemIds = new Set<string>();
      const leaderScopedIds = new Set<string>();
      let lastLeaderAssistantId: string | undefined;
      let firstLeaderItemId: string | undefined;

      for (const item of leaderItems) {
        leaderScopedIds.add(item.id);
        if (!firstLeaderItemId) {
          firstLeaderItemId = item.id;
        }
        if (item.kind === 'reasoning') {
          reasoningParts.push(item.text);
          leaderReasoningItemIds.add(item.id);
        }
        if (item.kind === 'assistant') {
          lastLeaderAssistantId = item.id;
        }
      }

      views.push({
        turnKey,
        reasoningText: reasoningParts.join('\n\n').trim(),
        leaderReasoningItemIds,
        lastLeaderAssistantId,
        leaderScopedIds,
        firstLeaderItemId,
        isLatestTurn: false,
      });
    }

    if (!atEnd) {
      turnKey = items[index].id;
      sliceStart = index + 1;
    }
  }

  if (views.length > 0) {
    views[views.length - 1] = { ...views[views.length - 1], isLatestTurn: true };
  }
  return views;
}

export function indexLeaderTurnViews(views: LeaderTurnView[]): {
  byTurnKey: Map<string, LeaderTurnView>;
  byItemId: Map<string, LeaderTurnView>;
} {
  const byTurnKey = new Map<string, LeaderTurnView>();
  const byItemId = new Map<string, LeaderTurnView>();
  for (const view of views) {
    byTurnKey.set(view.turnKey, view);
    for (const id of view.leaderScopedIds) {
      byItemId.set(id, view);
    }
  }
  return { byTurnKey, byItemId };
}

export function isLeaderTurnComplete(view: LeaderTurnView, streaming: boolean): boolean {
  return Boolean(view.lastLeaderAssistantId) && (!view.isLatestTurn || !streaming);
}

/** Resolve leader turn for grouped tools (`tool-group-*` ids are synthetic). */
export function leaderTurnForItem(
  item: ChatItem,
  byItemId: Map<string, LeaderTurnView>,
): LeaderTurnView | undefined {
  const direct = byItemId.get(item.id);
  if (direct) {
    return direct;
  }
  if (item.kind === 'tool-group') {
    for (const tool of item.tools) {
      const turn = byItemId.get(tool.id);
      if (turn) {
        return turn;
      }
    }
  }
  return undefined;
}
