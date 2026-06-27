import type { ChatItem, ToolChatItem } from '../types/events';
import { isTeamSurfacePayload, parseToolEnd, parseToolStart } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';

/** Member team-surface tool.start within this seq window after a leader tool.start ⇒ leader echo. */
export const MEMBER_MIRROR_SEQ_WINDOW = 12;

function normalizeToolName(toolName: string): string {
  return toolName.trim().toLowerCase();
}

function isTeamOrchestrationTool(toolName: string): boolean {
  const normalized = normalizeToolName(toolName);
  return normalized.includes('build_team')
    || (normalized.includes('send_message') && normalized.includes('team'));
}

function isDelegationTeamTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('build_team') || normalized.includes('send_message');
}

function toolKey(
  tool: Pick<ToolChatItem, 'toolCallId' | 'seq' | 'toolName'>,
): string {
  const name = normalizeToolName(tool.toolName);
  if (tool.toolCallId) {
    return `id:${tool.toolCallId}`;
  }
  return `seq:${tool.seq ?? 0}|${name}`;
}

/**
 * Member sub-runs emit team-surface tool.start/end and a leader-scope mirror (no memberId).
 * The mirror shares toolName and lands a few seq later — drop it from the leader workspace lane.
 */
export function buildMirroredLeaderWorkspaceToolKeys(events: RunEventRow[]): Set<string> {
  const memberStarts: { seq: number; toolName: string }[] = [];
  for (const event of events) {
    if (event.name !== 'tool.start' || !event.data || !isTeamSurfacePayload(event.data)) {
      continue;
    }
    const data = event.data as Record<string, unknown>;
    const memberId = typeof data.memberId === 'string' ? data.memberId : '';
    if (!memberId) {
      continue;
    }
    memberStarts.push({
      seq: event.seq,
      toolName: normalizeToolName(parseToolStart(event.data).toolName),
    });
  }

  const keys = new Set<string>();
  for (const event of events) {
    if (event.name !== 'tool.start' || !event.data || isTeamSurfacePayload(event.data)) {
      continue;
    }
    const payload = parseToolStart(event.data);
    if (isTeamOrchestrationTool(payload.toolName)) {
      continue;
    }
    const toolName = normalizeToolName(payload.toolName);
    const mirrored = memberStarts.some(
      (member) =>
        member.toolName === toolName
        && member.seq >= event.seq
        && member.seq <= event.seq + MEMBER_MIRROR_SEQ_WINDOW,
    );
    if (!mirrored) {
      continue;
    }
    keys.add(toolKey({
      toolCallId: payload.toolCallId,
      seq: event.seq,
      toolName: payload.toolName,
    }));
  }
  return keys;
}

export function isMirroredLeaderWorkspaceTool(
  tool: Pick<ToolChatItem, 'toolCallId' | 'seq' | 'toolName'>,
  mirroredKeys: Set<string>,
): boolean {
  return mirroredKeys.has(toolKey(tool));
}

export function filterMirroredLeaderWorkspaceTools(
  items: ToolChatItem[],
  events: RunEventRow[],
): ToolChatItem[] {
  const mirroredKeys = buildMirroredLeaderWorkspaceToolKeys(events);
  if (mirroredKeys.size === 0) {
    return items;
  }
  return items.filter((item) => !isMirroredLeaderWorkspaceTool(item, mirroredKeys));
}

/** Leader workspace tools (bash/read/write/…) from run_events — excluded from teamToolItemsFromRunEvents. */
export function leaderWorkspaceToolItemsFromRunEvents(events: RunEventRow[]): ToolChatItem[] {
  const mirroredKeys = buildMirroredLeaderWorkspaceToolKeys(events);
  const items: ToolChatItem[] = [];
  const byCallId = new Map<string, ToolChatItem>();

  for (const event of events) {
    if (isTeamSurfacePayload(event.data)) {
      continue;
    }
    if (event.name === 'tool.start' && event.data) {
      const payload = parseToolStart(event.data);
      if (isDelegationTeamTool(payload.toolName)) {
        continue;
      }
      const draft: ToolChatItem = {
        id: payload.toolCallId ?? `tool-${event.seq}`,
        kind: 'tool',
        toolName: payload.toolName,
        toolCallId: payload.toolCallId,
        status: 'executing',
        args: payload.args,
        seq: event.seq,
        startedAt: event.seq,
      };
      if (isMirroredLeaderWorkspaceTool(draft, mirroredKeys)) {
        continue;
      }
      items.push(draft);
      if (payload.toolCallId) {
        byCallId.set(payload.toolCallId, draft);
      }
      continue;
    }
    if (event.name === 'tool.end' && event.data) {
      const payload = parseToolEnd(event.data);
      if (isDelegationTeamTool(payload.toolName)) {
        continue;
      }
      if (payload.toolCallId && mirroredKeys.has(`id:${payload.toolCallId}`)) {
        continue;
      }
      const existing = payload.toolCallId ? byCallId.get(payload.toolCallId) : undefined;
      if (existing) {
        existing.status = payload.status;
        existing.result = payload.result;
        existing.endedAt = event.seq;
      } else if (!isMirroredLeaderWorkspaceTool(
        {
          toolCallId: payload.toolCallId,
          seq: event.seq,
          toolName: payload.toolName,
        },
        mirroredKeys,
      )) {
        items.push({
          id: payload.toolCallId ?? `tool-end-${event.seq}`,
          kind: 'tool',
          toolName: payload.toolName,
          toolCallId: payload.toolCallId,
          status: payload.status,
          result: payload.result,
          seq: event.seq,
          startedAt: event.seq,
          endedAt: event.seq,
        });
      }
    }
  }

  return items;
}

export function enrichLeaderWorkspaceTools(
  eventItems: ToolChatItem[],
  messages: ChatItem[],
): ToolChatItem[] {
  const byCallId = new Map<string, ToolChatItem>();
  for (const item of eventItems) {
    if (item.toolCallId) {
      byCallId.set(item.toolCallId, item);
    }
  }
  for (const item of messages) {
    if (item.kind !== 'tool' || item.memberId || !item.toolCallId) {
      continue;
    }
    const existing = byCallId.get(item.toolCallId);
    if (!existing) {
      continue;
    }
    if (item.args && Object.keys(item.args).length > 0) {
      existing.args = item.args;
    }
    if (item.result != null) {
      existing.result = item.result;
    }
  }
  return eventItems;
}

export function filterMirroredLeaderWorkspaceToolsFromMessages(
  messages: ChatItem[],
  events: RunEventRow[],
): ChatItem[] {
  const mirroredKeys = buildMirroredLeaderWorkspaceToolKeys(events);
  if (mirroredKeys.size === 0) {
    return messages;
  }
  return messages.filter((item) => {
    if (item.kind !== 'tool' || item.memberId) {
      return true;
    }
    return !isMirroredLeaderWorkspaceTool(item, mirroredKeys);
  });
}
