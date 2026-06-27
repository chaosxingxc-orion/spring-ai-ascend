/** W38 — Share replay / optional ACP dual-path chat projection. */

import type { ChatItem, PlanStep } from '../../types/events';
import { parseChatItems, parsePlanCreate } from '../eventPayload';
import { parseUserAttachments } from '../userAttachments';
import { isTeamSurfacePayload, type RunEventRow } from '../memberEventProjection';
import { reasoningItemsFromRunEvents } from '../reasoningHydrate';
import { runEventToAcp } from './runEventToAcp';
import { accumulateAcpStream } from './acpMessageAccumulator';
import type { AcpSessionUpdate } from './runEventToAcp';

export type ShareChatProjectionMode = 'messages' | 'run-events' | 'acp-roundtrip';

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function readString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function normalizeRunEvents(events: ShareReplayEvent[]): RunEventRow[] {
  return events.map((event) => ({
    seq: typeof event.seq === 'number' ? event.seq : 0,
    name: String(event.name ?? ''),
    data: isRecord(event.data) ? event.data : {},
  }));
}

export interface ShareReplayEvent {
  seq: number;
  name: string;
  data?: Record<string, unknown>;
}

function chatItemsFromRunEventsNative(events: RunEventRow[]): ChatItem[] {
  const items: ChatItem[] = [];
  let assistantText = '';
  let assistantId = 'assistant-replay';
  const tools = new Map<string, Extract<ChatItem, { kind: 'tool' }>>();

  const flushAssistant = () => {
    if (assistantText.trim()) {
      items.push({ id: assistantId, kind: 'assistant', text: assistantText });
    }
    assistantText = '';
  };

  for (const event of events) {
    if (isTeamSurfacePayload(event.data)) {
      continue;
    }
    const data = event.data ?? {};
    switch (event.name) {
      case 'message.user':
        flushAssistant();
        items.push({
          id: `user-${event.seq}`,
          kind: 'user',
          text: readString(data.text) ?? '',
          seq: event.seq,
          attachments: (() => {
            const parsed = parseUserAttachments(data.attachments);
            return parsed.length > 0 ? parsed : undefined;
          })(),
        });
        break;
      case 'message.delta':
        assistantText += readString(data.text) ?? '';
        assistantId = `assistant-${event.seq}`;
        break;
      case 'reasoning.delta':
      case 'reasoning':
        flushAssistant();
        items.push({
          id: `reasoning-${event.seq}`,
          kind: 'reasoning',
          text: readString(data.text) ?? '',
        });
        break;
      case 'tool.start': {
        flushAssistant();
        const toolCallId = readString(data.toolCallId) ?? `tool-${event.seq}`;
        const toolItem = {
          id: toolCallId,
          kind: 'tool' as const,
          toolName: readString(data.toolName) ?? 'tool',
          toolCallId,
          status: 'executing' as const,
          args: data.args,
        };
        tools.set(toolCallId, toolItem);
        items.push(toolItem);
        break;
      }
      case 'tool.end': {
        const toolCallId = readString(data.toolCallId);
        const existing = toolCallId ? tools.get(toolCallId) : undefined;
        const success = isRecord(data.result) && data.result.success !== false;
        if (existing) {
          existing.status = success ? 'success' : 'failed';
          existing.result = data.result;
        }
        break;
      }
      case 'plan.create':
      case 'plan.update': {
        flushAssistant();
        const plan = parsePlanCreate(data);
        if (plan) {
          items.push({
            id: `plan-${plan.planId}`,
            kind: 'plan',
            planId: plan.planId,
            title: plan.title,
            steps: plan.steps as PlanStep[],
          });
        }
        break;
      }
      default:
        break;
    }
  }
  flushAssistant();
  return items;
}

function chatItemsFromAcpRoundTrip(events: RunEventRow[]): ChatItem[] {
  const acpUpdates: AcpSessionUpdate[] = [];
  for (const event of events) {
    if (isTeamSurfacePayload(event.data)) {
      continue;
    }
    const acp = runEventToAcp({
      seq: event.seq,
      name: event.name,
      data: event.data ?? {},
    });
    if (acp) {
      acpUpdates.push(acp);
    }
  }
  const drafts = accumulateAcpStream(acpUpdates);
  const roundTripEvents: RunEventRow[] = drafts.map((draft, index) => ({
    seq: index + 1,
    name: draft.name,
    data: draft.data,
  }));
  return chatItemsFromRunEventsNative(roundTripEvents.length > 0 ? roundTripEvents : events);
}

/** Project share replay chat items (messages default; run-events / acp-roundtrip for W38). */
export function projectShareChatItems(
  messages: unknown[],
  events: ShareReplayEvent[],
  mode: ShareChatProjectionMode = 'messages',
): ChatItem[] {
  const normalized = normalizeRunEvents(events);
  if (mode === 'run-events') {
    return chatItemsFromRunEventsNative(normalized);
  }
  if (mode === 'acp-roundtrip') {
    return chatItemsFromAcpRoundTrip(normalized);
  }
  const base = parseChatItems(messages);
  const reasoning = reasoningItemsFromRunEvents(normalized);
  return [...base, ...reasoning];
}

export function parseShareProjectionMode(value: string | null): ShareChatProjectionMode {
  if (value === 'run-events' || value === 'acp-roundtrip') {
    return value;
  }
  return 'messages';
}
