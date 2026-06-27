/** W36-A5 / W38 / W39-B3 — member sub-run run_events projection helpers. */

import type { ToolStatus } from '../types/events';

export interface RunEventRow {
  seq: number;
  name: string;
  data?: Record<string, unknown>;
}

export interface MemberTraceItem {
  seq: number;
  kind: 'assistant' | 'tool' | 'reasoning';
  id: string;
  text?: string;
  toolName?: string;
  toolCallId?: string;
  status?: ToolStatus;
  args?: unknown;
  result?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function readString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

/** W36-A5 — team-surface payloads must not enter main chat projection. */
export function isTeamSurfacePayload(data: unknown): boolean {
  if (!isRecord(data)) {
    return false;
  }
  if (readString(data.surface) === 'team') {
    return true;
  }
  const memberId = readString(data.memberId);
  const parentRunId = readString(data.parentRunId);
  return memberId != null && parentRunId != null;
}

/** Leader/main run ended — member sub-run terminal events must not stop the main chat stream. */
export function isLeaderRunTerminal(name: string, data: unknown): boolean {
  if (name === 'team.completed') {
    return true;
  }
  if (name === 'run.completed' || name === 'run.failed' || name === 'run.error') {
    return !isTeamSurfacePayload(data);
  }
  return false;
}

export function isMemberRunEvent(data: unknown, memberId: string): boolean {
  if (!isTeamSurfacePayload(data) || !isRecord(data)) {
    return false;
  }
  return readString(data.memberId) === memberId;
}

export function filterMemberRunEvents(events: RunEventRow[], memberId: string): RunEventRow[] {
  return events.filter((event) => isMemberRunEvent(event.data ?? {}, memberId));
}

function toolEndStatus(result: unknown): ToolStatus {
  if (!isRecord(result)) {
    return 'success';
  }
  if (result.success === false) {
    return 'failed';
  }
  const data = result.data;
  if (isRecord(data) && data.success === false) {
    return 'failed';
  }
  return 'success';
}

/** Build ordered member trace items from run_events (tool.start/end pairing + delta merge). */
export function projectMemberTrace(events: RunEventRow[], memberId: string): MemberTraceItem[] {
  const filtered = filterMemberRunEvents(events, memberId);
  const items: MemberTraceItem[] = [];
  const toolsByCallId = new Map<string, MemberTraceItem>();
  let assistantBuffer = '';
  let assistantSeq = 0;
  let reasoningBuffer = '';
  let reasoningSeq = 0;

  const flushAssistant = () => {
    const text = assistantBuffer.trim();
    if (!text) {
      assistantBuffer = '';
      return;
    }
    items.push({
      seq: assistantSeq,
      kind: 'assistant',
      id: `assistant-${assistantSeq}`,
      text: assistantBuffer,
    });
    assistantBuffer = '';
  };

  const flushReasoning = () => {
    const text = reasoningBuffer.trim();
    if (!text) {
      reasoningBuffer = '';
      return;
    }
    items.push({
      seq: reasoningSeq,
      kind: 'reasoning',
      id: `reasoning-${reasoningSeq}`,
      text: reasoningBuffer,
    });
    reasoningBuffer = '';
  };

  for (const event of filtered) {
    const data = event.data ?? {};
    switch (event.name) {
      case 'message.delta': {
        flushReasoning();
        assistantBuffer += readString(data.text) ?? '';
        assistantSeq = event.seq;
        break;
      }
      case 'reasoning.delta':
      case 'reasoning': {
        flushAssistant();
        reasoningBuffer += readString(data.text) ?? '';
        reasoningSeq = event.seq;
        break;
      }
      case 'tool.start': {
        flushAssistant();
        flushReasoning();
        const toolCallId = readString(data.toolCallId) ?? `seq-${event.seq}`;
        const item: MemberTraceItem = {
          seq: event.seq,
          kind: 'tool',
          id: toolCallId,
          toolName: readString(data.toolName) ?? 'tool',
          toolCallId,
          status: 'executing',
          args: data.args,
        };
        toolsByCallId.set(toolCallId, item);
        items.push(item);
        break;
      }
      case 'tool.end': {
        flushAssistant();
        flushReasoning();
        const toolCallId = readString(data.toolCallId) ?? `seq-${event.seq}`;
        const toolName = readString(data.toolName) ?? 'tool';
        const status = toolEndStatus(data.result);
        const existing = toolsByCallId.get(toolCallId);
        if (existing) {
          existing.status = status;
          existing.result = data.result;
        } else {
          items.push({
            seq: event.seq,
            kind: 'tool',
            id: toolCallId,
            toolName,
            toolCallId,
            status,
            result: data.result,
          });
        }
        break;
      }
      default:
        break;
    }
  }

  flushAssistant();
  flushReasoning();
  return items;
}

/** ACP inbound drafts → run_events rows (for replay / tests). */
export function draftsToRunEventRows(
  drafts: { name: string; data: Record<string, unknown> }[],
  startSeq = 1,
): RunEventRow[] {
  return drafts.map((draft, index) => ({
    seq: startSeq + index,
    name: draft.name,
    data: draft.data,
  }));
}