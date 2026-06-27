import type { ChatItem, ToolChatItem } from '../types/events';
import { sortChatItemsByOrder } from './chatItemOrder';
import { isTeamSurfacePayload, parseToolEnd, parseToolStart, readRedactableText } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';

export function mapRecordedEventsToRunEventRows(
  events: ReadonlyArray<{ seq?: number; name?: string; data?: unknown }>,
): RunEventRow[] {
  return events.map((entry) => ({
    seq: typeof entry.seq === 'number' ? entry.seq : 0,
    name: String(entry.name ?? ''),
    data: (entry.data ?? {}) as Record<string, unknown>,
  }));
}

/** Member-scoped narrative / tool cards (leader + structural rows are preserved). */
export function isMemberScopedChatItem(item: ChatItem): boolean {
  if (item.kind === 'assistant' || item.kind === 'reasoning' || item.kind === 'tool') {
    return Boolean(item.memberId);
  }
  return false;
}

/**
 * Re-project member sub-run items from run_events into the main chat timeline.
 * Leader scope and structural cards stay as-is; member filter in the UI is a view only.
 */
export function refreshChatMemberSurface(items: ChatItem[], events: RunEventRow[]): ChatItem[] {
  const preserved = items.filter((item) => !isMemberScopedChatItem(item));
  const memberItems = memberSurfaceItemsFromRunEvents(events);
  if (memberItems.length === 0 && preserved.length === items.length) {
    return items;
  }
  return sortChatItemsByOrder([...preserved, ...memberItems]);
}

function readString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function readRedactedString(value: unknown): string {
  if (typeof value === 'string') {
    return value;
  }
  if (value && typeof value === 'object') {
    const preview = (value as Record<string, unknown>).preview;
    return typeof preview === 'string' ? preview : '';
  }
  return '';
}

function readDelta(data: Record<string, unknown> | undefined): string {
  if (!data) {
    return '';
  }
  return (
    readRedactableText(data.delta)
    ?? readRedactableText(data.text)
    ?? readRedactableText(data.content)
    ?? ''
  );
}

function toolEndStatus(result: unknown): ToolChatItem['status'] {
  if (!result || typeof result !== 'object') {
    return 'success';
  }
  const record = result as Record<string, unknown>;
  if (record.success === false) {
    return 'failed';
  }
  const data = record.data;
  if (data && typeof data === 'object' && (data as Record<string, unknown>).success === false) {
    return 'failed';
  }
  return 'success';
}

function itemOrderKey(item: ChatItem): number {
  if (item.kind === 'tool') {
    return item.seq ?? item.startedAt ?? 0;
  }
  if (item.kind === 'user' || item.kind === 'assistant' || item.kind === 'reasoning') {
    return item.seq ?? 0;
  }
  return 0;
}

function isDelegationSendTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('send_message') && normalized.includes('team');
}

/** team.* orchestration tools are leader-scope cards owned by teamRunEventHydrate. */
function isDelegationTeamTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('build_team') || (normalized.includes('send_message') && normalized.includes('team'));
}

function delegationTargetFromData(data: Record<string, unknown>): { memberId: string; memberName?: string } | null {
  const toolName = readString(data.toolName);
  if (!isDelegationSendTool(toolName)) {
    return null;
  }
  const args = data.args && typeof data.args === 'object' ? (data.args as Record<string, unknown>) : null;
  const result = data.result && typeof data.result === 'object' ? (data.result as Record<string, unknown>) : null;
  const resultData = result?.data && typeof result.data === 'object'
    ? (result.data as Record<string, unknown>)
    : null;
  const routing = args?.routing && typeof args.routing === 'object'
    ? (args.routing as Record<string, unknown>)
    : null;
  const stripMention = (value?: string): string | undefined =>
    value ? value.replace(/^@/, '') : value;
  const memberId = stripMention(
    readRedactedString(args?.memberId)
    || readRedactedString(args?.to)
    || readRedactedString(args?.recipient)
    || readRedactedString(args?.target)
    || readRedactedString(routing?.target)
    || readString(resultData?.memberId),
  );
  if (!memberId) {
    return null;
  }
  const memberName = readRedactedString(args?.memberName)
    || readString(resultData?.memberName)
    || undefined;
  return { memberId, memberName };
}

/** Build member sub-run chat items (team-surface run_events) for the main timeline. */
export function memberSurfaceItemsFromRunEvents(events: RunEventRow[]): ChatItem[] {
  const items: ChatItem[] = [];
  const toolsByCallId = new Map<string, ToolChatItem>();
  const assistantByMember = new Map<string, { text: string; seq: number; memberName?: string }>();
  const reasoningByMember = new Map<string, { text: string; seq: number; memberName?: string }>();
  const lastLifecycleByMember = new Map<string, { index: number; eventName: string }>();

  const flushAssistant = (memberId: string) => {
    const buffer = assistantByMember.get(memberId);
    if (!buffer?.text) {
      assistantByMember.delete(memberId);
      return;
    }
    items.push({
      id: `member-assistant-${memberId}-${buffer.seq}`,
      kind: 'assistant',
      text: buffer.text,
      seq: buffer.seq,
      memberId,
      memberName: buffer.memberName,
    });
    assistantByMember.delete(memberId);
  };

  const flushReasoning = (memberId: string) => {
    const buffer = reasoningByMember.get(memberId);
    if (!buffer?.text.trim()) {
      reasoningByMember.delete(memberId);
      return;
    }
    items.push({
      id: `member-reasoning-${memberId}-${buffer.seq}`,
      kind: 'reasoning',
      text: buffer.text,
      seq: buffer.seq,
      memberId,
      memberName: buffer.memberName,
    });
    reasoningByMember.delete(memberId);
  };

  for (const event of events) {
    const data = (event.data ?? {}) as Record<string, unknown>;
    const delegationTarget = delegationTargetFromData(data);
    if (!isTeamSurfacePayload(event.data) && !delegationTarget) {
      continue;
    }
    const memberId = readString(data.memberId) || delegationTarget?.memberId || '';
    if (!memberId) {
      continue;
    }
    const memberName = readString(data.memberName) || delegationTarget?.memberName || '';

    switch (event.name) {
      case 'message.delta': {
        flushReasoning(memberId);
        lastLifecycleByMember.delete(memberId);
        const existing = assistantByMember.get(memberId) ?? { text: '', seq: event.seq, memberName };
        existing.text += readDelta(data);
        existing.seq = existing.text ? existing.seq : event.seq;
        if (memberName) {
          existing.memberName = memberName;
        }
        assistantByMember.set(memberId, existing);
        break;
      }
      case 'reasoning.delta':
      case 'reasoning': {
        flushAssistant(memberId);
        lastLifecycleByMember.delete(memberId);
        const existing = reasoningByMember.get(memberId) ?? { text: '', seq: event.seq, memberName };
        existing.text += readDelta(data);
        if (memberName) {
          existing.memberName = memberName;
        }
        reasoningByMember.set(memberId, existing);
        break;
      }
      case 'tool.start': {
        const startName = readString(data.toolName);
        if (isDelegationTeamTool(startName)) {
          // Leader-scope orchestration card; rendered in the leader timeline by teamRunEventHydrate.
          break;
        }
        flushAssistant(memberId);
        flushReasoning(memberId);
        const payload = parseToolStart(data);
        const id = payload.toolCallId ?? `member-tool-${event.seq}`;
        const item: ToolChatItem = {
          id,
          kind: 'tool',
          toolName: payload.toolName,
          toolCallId: payload.toolCallId,
          status: 'executing',
          args: payload.args,
          seq: event.seq,
          startedAt: event.seq,
          memberId,
          memberName,
        };
        items.push(item);
        if (payload.toolCallId) {
          toolsByCallId.set(payload.toolCallId, item);
        }
        lastLifecycleByMember.delete(memberId);
        break;
      }
      case 'tool.end': {
        const endName = readString(data.toolName);
        if (isDelegationTeamTool(endName)) {
          break;
        }
        flushAssistant(memberId);
        flushReasoning(memberId);
        const payload = parseToolEnd(data);
        const existing = payload.toolCallId ? toolsByCallId.get(payload.toolCallId) : undefined;
        if (existing) {
          existing.status = toolEndStatus(payload.result);
          existing.result = payload.result;
          existing.endedAt = event.seq;
        } else {
          items.push({
            id: payload.toolCallId ?? `member-tool-end-${event.seq}`,
            kind: 'tool',
            toolName: payload.toolName,
            toolCallId: payload.toolCallId,
            status: toolEndStatus(payload.result),
            result: payload.result,
            seq: event.seq,
            startedAt: event.seq,
            memberId,
            memberName,
          });
        }
        lastLifecycleByMember.delete(memberId);
        break;
      }
      case 'team.member.started':
      case 'team.member.reawakened':
      case 'team.member.completed':
      case 'team.member.paused':
      case 'team.member.failed': {
        // started/reawakened are represented by delegation tool cards, avoid noisy duplicates.
        if (event.name === 'team.member.started' || event.name === 'team.member.reawakened') {
          break;
        }
        flushAssistant(memberId);
        flushReasoning(memberId);
        const statusText =
          event.name === 'team.member.completed'
            ? (readString(data.summary) || '任务完成')
            : event.name === 'team.member.paused'
              ? '已暂停，等待下一轮…'
              : (readString(data.error) || '任务失败');
        const previous = lastLifecycleByMember.get(memberId);
        const compactible =
          previous
          && (event.name === 'team.member.completed' || event.name === 'team.member.paused')
          && (previous.eventName === 'team.member.completed' || previous.eventName === 'team.member.paused');
        if (compactible) {
          const prevItem = items[previous.index];
          if (prevItem && prevItem.kind === 'reasoning') {
            prevItem.text = statusText;
            prevItem.seq = event.seq;
          }
          lastLifecycleByMember.set(memberId, { index: previous.index, eventName: event.name });
        } else {
          items.push({
            id: `member-lifecycle-${memberId}-${event.seq}`,
            kind: 'reasoning',
            text: statusText,
            seq: event.seq,
            memberId,
            memberName,
          });
          lastLifecycleByMember.set(memberId, { index: items.length - 1, eventName: event.name });
        }
        break;
      }
      default:
        break;
    }
  }

  for (const memberId of assistantByMember.keys()) {
    flushAssistant(memberId);
  }
  for (const memberId of reasoningByMember.keys()) {
    flushReasoning(memberId);
  }

  return items.sort((a, b) => itemOrderKey(a) - itemOrderKey(b));
}

export function mergeChatWithMemberSurfaceEvents(
  items: ChatItem[],
  events: RunEventRow[],
): ChatItem[] {
  const memberItems = memberSurfaceItemsFromRunEvents(events);
  if (memberItems.length === 0) {
    return items;
  }

  const existingIds = new Set(items.map((item) => item.id));
  const merged = [...items];
  for (const item of memberItems) {
    if (existingIds.has(item.id)) {
      const existingIndex = merged.findIndex((entry) => entry.id === item.id);
      const existing = existingIndex >= 0 ? merged[existingIndex] : null;
      if (existing && existing.kind === item.kind) {
        if (item.kind === 'assistant' || item.kind === 'reasoning') {
          merged[existingIndex] = { ...existing, ...item, text: item.text };
          continue;
        }
        if (item.kind === 'tool') {
          merged[existingIndex] = {
            ...existing,
            ...item,
            memberId: item.memberId ?? existing.memberId,
            memberName: item.memberName ?? existing.memberName,
          };
          continue;
        }
      }
      if (
        existing
        && existing.kind === 'tool'
        && item.kind === 'tool'
        && !existing.memberId
        && item.memberId
      ) {
        merged[existingIndex] = {
          ...existing,
          memberId: item.memberId,
          memberName: item.memberName ?? existing.memberName,
        };
      }
      continue;
    }
    const targetSeq = itemOrderKey(item);
    let insertAt = merged.length;
    for (let index = 0; index < merged.length; index += 1) {
      const seq = itemOrderKey(merged[index]);
      if (typeof seq === 'number' && seq > targetSeq) {
        insertAt = index;
        break;
      }
    }
    merged.splice(insertAt, 0, item);
    existingIds.add(item.id);
  }
  return merged;
}
