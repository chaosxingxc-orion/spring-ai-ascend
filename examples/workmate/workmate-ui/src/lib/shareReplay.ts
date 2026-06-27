import type { ChatItem } from '../types/events';

export interface UserTurn {
  userIndex: number;
  endIndex: number;
}

export function buildUserTurns(items: ChatItem[]): UserTurn[] {
  const turns: UserTurn[] = [];
  for (let index = 0; index < items.length; index += 1) {
    if (items[index].kind !== 'user') {
      continue;
    }
    let endIndex = items.length - 1;
    for (let next = index + 1; next < items.length; next += 1) {
      if (items[next].kind === 'user') {
        endIndex = next - 1;
        break;
      }
    }
    turns.push({ userIndex: index, endIndex });
  }
  return turns;
}

export function sliceItemsForReplay(items: ChatItem[], visibleEndIndex: number): ChatItem[] {
  if (visibleEndIndex < 0) {
    return [];
  }
  return items.slice(0, Math.min(visibleEndIndex + 1, items.length));
}

export function replayStatusLabel(
  playing: boolean,
  visibleEndIndex: number,
  total: number,
): string {
  if (total === 0) {
    return '无消息';
  }
  if (visibleEndIndex >= total - 1) {
    return playing ? '回放中 · 已完成' : '已完成';
  }
  return playing ? `回放中 · ${visibleEndIndex + 1}/${total}` : `暂停 · ${visibleEndIndex + 1}/${total}`;
}
