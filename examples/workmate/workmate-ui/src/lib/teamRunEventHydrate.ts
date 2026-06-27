import type { ChatItem } from '../types/events';
import type { RunEventRow } from './reasoningHydrate';
import {
  isTeamSurfacePayload,
  parseTeamBuildCompleted,
  parseToolEnd,
  parseToolStart,
  readRedactableText,
} from './eventPayload';

function readString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

/** Human-readable line for {@code team.member.message} (user @member bypass). */
export function formatTeamBypassSystemText(data: Record<string, unknown>): string {
  const fromLabel = readString(data.fromLabel) || '用户';
  const to = readString(data.to) || '*';
  const message = readRedactableText(data.message) ?? readString(data.message);
  return `${fromLabel} 旁路 → ${to}：${message}`;
}

export function teamBypassSystemItemsFromRunEvents(events: RunEventRow[]): ChatItem[] {
  const items: ChatItem[] = [];
  for (const event of events) {
    if (event.name !== 'team.member.message' || !event.data || typeof event.data !== 'object') {
      continue;
    }
    items.push({
      id: `team-bypass-${event.seq}`,
      kind: 'system',
      text: formatTeamBypassSystemText(event.data as Record<string, unknown>),
      tone: 'info',
      seq: event.seq,
    });
  }
  return items;
}

function isTeamOrchestrationTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('build_team')
    || (normalized.includes('send_message') && normalized.includes('team'));
}

function isDelegationTeamTool(toolName: string): boolean {
  return isTeamOrchestrationTool(toolName);
}

interface PendingSendTool {
  itemId: string;
  memberId: string;
}

/**
 * Pre-scan to learn whether the backend already emitted native delegation tool events. Native and
 * synthetic cards have different ids and would otherwise both appear (duplicate "派活" / "建团队"
 * cards). Because the native tool.start arrives *after* its triggering lifecycle event, a single
 * forward pass cannot decide this inline — so we scan first.
 */
function scanNativeDelegationTools(events: RunEventRow[]): { build: boolean; send: boolean } {
  let build = false;
  let send = false;
  for (const event of events) {
    if (event.name !== 'tool.start' || !event.data || isTeamSurfacePayload(event.data)) {
      continue;
    }
    const toolName = parseToolStart(event.data).toolName.toLowerCase();
    if (toolName.includes('build_team')) {
      build = true;
    }
    if (toolName.includes('send_message') && toolName.includes('team')) {
      send = true;
    }
  }
  return { build, send };
}

/** Build TeamCreate / SendMessage tool cards from run_events (native or synthesized). */
export function teamToolItemsFromRunEvents(events: RunEventRow[]): ChatItem[] {
  const items: ChatItem[] = [];
  const itemById = new Map<string, ChatItem & { kind: 'tool' }>();
  const pendingSendByMember = new Map<string, PendingSendTool>();
  const native = scanNativeDelegationTools(events);
  const hasNativeBuildTool = native.build;
  const hasNativeSendTool = native.send;
  // team.build.completed is re-delivered when the leader run is reawakened (resume), so the same
  // team emits it more than once. Synthesize the TeamCreate card only on the first sighting per
  // team — otherwise the leader timeline shows duplicate "团队已创建" cards.
  const synthesizedBuildTeams = new Set<string>();

  for (const event of events) {
    if (event.name === 'tool.start' && event.data && !isTeamSurfacePayload(event.data)) {
      const payload = parseToolStart(event.data);
      if (!isDelegationTeamTool(payload.toolName)) {
        continue;
      }
      const id = payload.toolCallId ?? `tool-${event.seq}`;
      const item: ChatItem = {
        id,
        kind: 'tool',
        toolName: payload.toolName,
        toolCallId: payload.toolCallId,
        status: 'executing',
        args: payload.args,
        seq: event.seq,
        startedAt: event.seq,
      };
      items.push(item);
      itemById.set(id, item as ChatItem & { kind: 'tool' });
      continue;
    }

    if (event.name === 'tool.end' && event.data && !isTeamSurfacePayload(event.data)) {
      const payload = parseToolEnd(event.data);
      if (!isDelegationTeamTool(payload.toolName)) {
        continue;
      }
      const id = payload.toolCallId ?? `tool-end-${event.seq}`;
      const existing = payload.toolCallId ? itemById.get(payload.toolCallId) : undefined;
      if (existing) {
        existing.status = payload.status;
        existing.result = payload.result;
      } else {
        items.push({
          id,
          kind: 'tool',
          toolName: payload.toolName,
          toolCallId: payload.toolCallId,
          status: payload.status,
          result: payload.result,
          seq: event.seq,
          startedAt: event.seq,
        });
      }
      continue;
    }

    if (event.name === 'team.build.completed') {
      if (hasNativeBuildTool) {
        continue;
      }
      const build = parseTeamBuildCompleted(event.data);
      const buildKey = build?.teamName || build?.displayName || 'team';
      if (synthesizedBuildTeams.has(buildKey)) {
        continue;
      }
      synthesizedBuildTeams.add(buildKey);
      const id = `team-build-synth-${event.seq}`;
      items.push({
        id,
        kind: 'tool',
        toolName: 'team.build_team',
        toolCallId: id,
        status: 'success',
        args: {
          team_name: build?.teamName,
          display_name: build?.displayName,
          teamName: build?.teamName,
          displayName: build?.displayName,
        },
        result: { success: true, data: build },
        seq: event.seq,
        startedAt: event.seq,
      });
      continue;
    }

    // Synthesize a delegation card only when the backend did NOT emit native send_message tools
    // (legacy / synchronous topologies). Each spawn or re-task opens a card.
    if ((event.name === 'team.member.started' || event.name === 'team.member.reawakened')
        && !hasNativeSendTool
        && event.data) {
      const memberId = readString(event.data.memberId);
      if (!memberId) {
        continue;
      }
      const id = `team-send-synth-${event.seq}-${memberId}`;
      const item: ChatItem = {
        id,
        kind: 'tool',
        toolName: 'team.send_message',
        toolCallId: id,
        status: 'executing',
        args: {
          memberId,
          to: memberId,
          memberName: readString(event.data.memberName),
        },
        seq: event.seq,
        startedAt: event.seq,
      };
      items.push(item);
      itemById.set(id, item as ChatItem & { kind: 'tool' });
      pendingSendByMember.set(memberId, { itemId: id, memberId });
      continue;
    }

    if (event.name === 'team.member.completed' && event.data) {
      const memberId = readString(event.data.memberId);
      const pending = memberId ? pendingSendByMember.get(memberId) : undefined;
      if (!pending) {
        continue;
      }
      const existing = itemById.get(pending.itemId);
      if (existing) {
        existing.status = 'success';
        existing.result = {
          success: true,
          data: {
            memberId,
            memberName: readString(event.data.memberName),
          },
        };
        existing.endedAt = event.seq;
      }
      pendingSendByMember.delete(memberId);
      continue;
    }

    if (event.name === 'team.member.failed' && event.data) {
      const memberId = readString(event.data.memberId);
      const pending = memberId ? pendingSendByMember.get(memberId) : undefined;
      if (!pending) {
        continue;
      }
      const existing = itemById.get(pending.itemId);
      if (existing) {
        existing.status = 'failed';
        existing.result = {
          success: false,
          error: readString(event.data.error) || 'failed',
        };
        existing.endedAt = event.seq;
      }
      pendingSendByMember.delete(memberId);
      continue;
    }

    // Worker may pause before producing output (empty mailbox batch); close the synth card so it
    // does not stay on "正在派发任务…".
    if (event.name === 'team.member.paused' && event.data) {
      const memberId = readString(event.data.memberId);
      const pending = memberId ? pendingSendByMember.get(memberId) : undefined;
      if (!pending) {
        continue;
      }
      const existing = itemById.get(pending.itemId);
      if (existing && existing.status === 'executing') {
        existing.status = 'success';
        existing.result = {
          success: true,
          data: {
            memberId,
            memberName: readString(event.data.memberName),
            paused: true,
          },
        };
        existing.endedAt = event.seq;
      }
      pendingSendByMember.delete(memberId);
    }
  }

  return items.sort((a, b) => (itemOrderKey(a) - itemOrderKey(b)));
}

function itemOrderKey(item: ChatItem): number {
  if (item.kind === 'tool') {
    return item.seq ?? item.startedAt ?? 0;
  }
  if (
    item.kind === 'user'
    || item.kind === 'assistant'
    || item.kind === 'reasoning'
    || item.kind === 'question'
    || item.kind === 'approval'
    || item.kind === 'system'
  ) {
    return item.seq ?? 0;
  }
  return 0;
}

/** Merge team tool cards into chat timeline ordered by seq. */
export function mergeChatWithTeamToolEvents(
  items: ChatItem[],
  events: RunEventRow[],
): ChatItem[] {
  const toolItems = teamToolItemsFromRunEvents(events);
  if (toolItems.length === 0) {
    return items;
  }

  const existingIds = new Set(items.map((item) => item.id));
  const existingToolCallIds = new Set(
    items
      .filter((item): item is ChatItem & { kind: 'tool' } => item.kind === 'tool')
      .map((item) => item.toolCallId)
      .filter((id): id is string => Boolean(id)),
  );
  const merged = [...items];
  for (const tool of toolItems) {
    const toolCallId = tool.kind === 'tool' ? tool.toolCallId : undefined;
    if (existingIds.has(tool.id) || (toolCallId && existingToolCallIds.has(toolCallId))) {
      continue;
    }
    const targetSeq = itemOrderKey(tool);
    let insertAt = merged.length;
    for (let index = 0; index < merged.length; index += 1) {
      const seq = itemOrderKey(merged[index]);
      if (typeof seq === 'number' && seq > targetSeq) {
        insertAt = index;
        break;
      }
    }
    merged.splice(insertAt, 0, tool);
    existingIds.add(tool.id);
    if (toolCallId) {
      existingToolCallIds.add(toolCallId);
    }
  }
  return merged;
}
