import type { MemberTraceItem } from './memberEventProjection';
import { chatItemOrderKey } from './chatItemOrder';
import type { ChatItem } from '../types/events';

/** Member scope of a chat item (`undefined` ⇒ leader / main-agent scope). */
export function itemMemberId(item: ChatItem): string | undefined {
  if (item.kind === 'assistant' || item.kind === 'reasoning' || item.kind === 'tool') {
    return item.memberId;
  }
  return undefined;
}

export function userTargetsMember(item: ChatItem, member: { id: string; name: string } | null): boolean {
  if (!member || item.kind !== 'user') {
    return false;
  }
  const mentions = item.mentions ?? [];
  return mentions.some((mention) =>
    mention.type === 'member'
    && (mention.id === member.id || mention.id === member.name || mention.label === member.name));
}

/** Filter the main chat timeline to one member (same source as center member focus view). */
export function filterMemberChatItems(
  items: ChatItem[],
  member: { id: string; name: string } | null,
): ChatItem[] {
  if (!member) {
    return items;
  }
  return items.filter(
    (item) => itemMemberId(item) === member.id || userTargetsMember(item, member),
  );
}

function readItemSeq(item: ChatItem): number {
  if (item.kind === 'tool') {
    return item.seq ?? item.startedAt ?? 0;
  }
  if (item.kind === 'assistant' || item.kind === 'reasoning' || item.kind === 'user') {
    return item.seq ?? 0;
  }
  return chatItemOrderKey(item);
}

export function isMemberLifecycleStatusText(text: string | undefined): boolean {
  const trimmed = text?.trim() ?? '';
  if (!trimmed) {
    return false;
  }
  return trimmed === '已暂停，等待下一轮…'
    || trimmed === '任务完成'
    || trimmed.startsWith('任务失败');
}

function isMemberScopedTraceItem(
  item: ChatItem,
  memberId: string,
): item is Extract<ChatItem, { kind: 'assistant' } | { kind: 'reasoning' } | { kind: 'tool' }> {
  if (item.kind !== 'assistant' && item.kind !== 'reasoning' && item.kind !== 'tool') {
    return false;
  }
  return item.memberId === memberId;
}

/** Project member-scoped chat items into the detail-panel trace list. */
export function chatItemsToMemberTrace(items: ChatItem[], memberId: string): MemberTraceItem[] {
  return items
    .filter((item): item is Extract<ChatItem, { kind: 'assistant' } | { kind: 'reasoning' } | { kind: 'tool' }> =>
      isMemberScopedTraceItem(item, memberId))
    .map<MemberTraceItem>((item) => {
      const seq = readItemSeq(item);
      if (item.kind === 'tool') {
        return {
          seq,
          kind: 'tool',
          id: item.id,
          toolName: item.toolName,
          toolCallId: item.toolCallId,
          status: item.status,
          args: item.args,
          result: item.result,
        };
      }
      return {
        seq,
        kind: item.kind,
        id: item.id,
        text: item.text,
      };
    })
    .sort((a, b) => a.seq - b.seq);
}

export interface MemberTraceSummary {
  firstStartSeq: number | null;
  lastCompletedSeq: number | null;
  toolCalls: number;
  errorCount: number;
  unscheduled: boolean;
}

export function summarizeMemberChatItems(items: ChatItem[], memberId: string): MemberTraceSummary {
  let firstStartSeq: number | null = null;
  let lastCompletedSeq: number | null = null;
  let toolCalls = 0;
  let errorCount = 0;

  for (const item of items) {
    if (!isMemberScopedTraceItem(item, memberId)) {
      continue;
    }
    const seq = readItemSeq(item);
    if (firstStartSeq == null && seq > 0) {
      firstStartSeq = seq;
    }
    if (item.kind === 'tool') {
      toolCalls += 1;
      if (item.status === 'failed') {
        errorCount += 1;
      }
    }
    if (item.kind === 'reasoning' && item.text.trim() === '任务完成') {
      lastCompletedSeq = seq;
    }
    if (item.kind === 'reasoning' && item.text.startsWith('任务失败')) {
      errorCount += 1;
    }
  }

  return {
    firstStartSeq,
    lastCompletedSeq,
    toolCalls,
    errorCount,
    unscheduled: firstStartSeq == null,
  };
}
