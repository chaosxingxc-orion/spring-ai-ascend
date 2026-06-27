import type { ChatItem } from '../types/events';
import { isTeamSurfacePayload, readRedactableText } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';

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

function readMessageId(data: Record<string, unknown> | undefined): string | undefined {
  if (!data) {
    return undefined;
  }
  const direct = data.messageId ?? data.message_id;
  return typeof direct === 'string' && direct ? direct : undefined;
}

/** Fold main-conversation message.delta stream (excludes member sub-run surface). */
export function foldLeaderMessageDeltas(events: RunEventRow[]): string {
  let text = '';
  for (const event of events) {
    if (event.name !== 'message.delta' || isTeamSurfacePayload(event.data)) {
      continue;
    }
    text += readDelta(event.data);
  }
  return text;
}

function lastUserIndex(items: ChatItem[]): number {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    if (items[index]?.kind === 'user') {
      return index;
    }
  }
  return -1;
}

function lastAssistantIndexAfterUser(items: ChatItem[]): number {
  const userIndex = lastUserIndex(items);
  for (let index = items.length - 1; index > userIndex; index -= 1) {
    if (items[index]?.kind === 'assistant') {
      return index;
    }
  }
  return -1;
}

import { chatItemOrderKey, insertChatItemByOrder } from './chatItemOrder';

interface LeaderTurnSegment {
  id: string;
  text: string;
  firstSeq: number;
}

/**
 * Collect seq breakpoints that must split a leader narration bubble. Question/approval cards
 * already do this on the live path via closeLeaderTurnForInterjection; orchestration tools
 * (build_team / send_message) must split too so the tool card renders before post-tool text.
 */
function hasNativeBuildTeamTool(events: RunEventRow[]): boolean {
  return events.some((event) => {
    if (event.name !== 'tool.start' || isTeamSurfacePayload(event.data)) {
      return false;
    }
    const toolName = typeof event.data?.toolName === 'string'
      ? event.data.toolName.toLowerCase()
      : '';
    return toolName.includes('build_team');
  });
}

function leaderTurnBreakpoints(items: ChatItem[], events: RunEventRow[]): number[] {
  const fromItems = items
    .filter(
      (item): item is ChatItem & { seq: number } =>
        (item.kind === 'question' || item.kind === 'approval') && typeof item.seq === 'number',
    )
    .map((item) => item.seq);
  const fromTools = events
    .filter((event) => {
      if (event.name !== 'tool.start' || isTeamSurfacePayload(event.data)) {
        return false;
      }
      const toolName = typeof event.data?.toolName === 'string'
        ? event.data.toolName.toLowerCase()
        : '';
      return toolName.includes('build_team')
        || (toolName.includes('send_message') && toolName.includes('team'));
    })
    .map((event) => event.seq);
  // openjiuwen-team often emits team.build.completed without native tool.start/end; the synth
  // build_team card lands at that seq, so split narration there too.
  const fromBuildCompleted = hasNativeBuildTeamTool(events)
    ? []
    : events
      .filter((event) => event.name === 'team.build.completed')
      .map((event) => event.seq);
  return [...fromItems, ...fromTools, ...fromBuildCompleted].sort((a, b) => a - b);
}

/**
 * Split one messageId turn's ordered deltas into segments separated by interjection breakpoints.
 * Each segment becomes its own assistant bubble; the first keeps the messageId (so a persisted
 * assistant message is updated in place) and subsequent ones use `${id}#${n}`.
 */
function splitTurnSegments(
  id: string,
  deltas: { seq: number; text: string }[],
  breakpoints: number[],
): LeaderTurnSegment[] {
  const ordered = [...deltas].sort((a, b) => a.seq - b.seq);
  const segments: LeaderTurnSegment[] = [];
  let segIndex = 0;
  let curText = '';
  let curFirst = ordered.length > 0 ? ordered[0].seq : 0;
  let bp = 0;
  const flush = () => {
    if (!curText) {
      return;
    }
    segments.push({ id: segIndex === 0 ? id : `${id}#${segIndex}`, text: curText, firstSeq: curFirst });
    segIndex += 1;
    curText = '';
  };
  for (const delta of ordered) {
    let crossed = false;
    while (bp < breakpoints.length && breakpoints[bp] < delta.seq) {
      crossed = true;
      bp += 1;
    }
    if (crossed && curText) {
      flush();
    }
    if (!curText) {
      curFirst = delta.seq;
    }
    curText += delta.text;
  }
  flush();
  return segments;
}

/**
 * messageId-aware merge: the backend tags each leader delta with its turn's messageId, which is also
 * the persisted assistant message id. We fold per messageId and upsert the matching assistant item,
 * so per-turn segmentation is preserved (no clobbering / re-merging across tools on reload). A single
 * turn is further split at question/approval boundaries (see interjectionBreakpoints).
 */
function mergeLeaderDeltasByMessageId(items: ChatItem[], events: RunEventRow[]): ChatItem[] {
  const groups = new Map<string, { seq: number; text: string }[]>();
  for (const event of events) {
    if (event.name !== 'message.delta' || isTeamSurfacePayload(event.data)) {
      continue;
    }
    const id = readMessageId(event.data);
    if (!id) {
      continue;
    }
    const text = readDelta(event.data);
    if (!text) {
      continue;
    }
    const arr = groups.get(id) ?? [];
    arr.push({ seq: event.seq, text });
    groups.set(id, arr);
  }
  if (groups.size === 0) {
    return items;
  }

  const breakpoints = leaderTurnBreakpoints(items, events);
  const segments: LeaderTurnSegment[] = [];
  for (const [id, deltas] of groups) {
    segments.push(...splitTurnSegments(id, deltas, breakpoints));
  }

  let merged = [...items];
  // Insert segments at their chronological position.
  // per-session counter now (backend nextSeq high-water mark), so firstSeq is directly comparable
  // to the seq of persisted messages — insert by seq instead of dumping every turn at the tail,
  // otherwise multi-round sessions render out of order on reload.
  const ordered = segments.sort((a, b) => a.firstSeq - b.firstSeq);
  for (const segment of ordered) {
    if (!segment.text) {
      continue;
    }
    const idx = merged.findIndex((item) => item.id === segment.id && item.kind === 'assistant');
    if (idx >= 0) {
      const current = merged[idx];
      if (current.kind === 'assistant' && current.text !== segment.text) {
        merged[idx] = { ...current, text: segment.text };
      }
      continue;
    }
    const turn: ChatItem = { id: segment.id, kind: 'assistant', text: segment.text, seq: segment.firstSeq };
    merged = insertChatItemByOrder(merged, turn);
  }
  return merged;
}

/** Merge leader streaming text from run_events into the chat timeline (in-flight TeamAgent runs). */
export function mergeChatWithLeaderDeltas(
  items: ChatItem[],
  events: RunEventRow[],
): ChatItem[] {
  const hasMessageId = events.some(
    (event) =>
      event.name === 'message.delta'
      && !isTeamSurfacePayload(event.data)
      && readMessageId(event.data) != null,
  );
  if (hasMessageId) {
    return mergeLeaderDeltasByMessageId(items, events);
  }

  // Legacy fallback (runs streamed before per-turn messageId tagging): single folded bubble.
  const leaderText = foldLeaderMessageDeltas(events);
  if (!leaderText) {
    return items;
  }

  const merged = [...items];
  const assistantIndex = lastAssistantIndexAfterUser(merged);
  if (assistantIndex >= 0) {
    const current = merged[assistantIndex];
    if (current.kind === 'assistant' && current.text !== leaderText) {
      merged[assistantIndex] = { ...current, text: leaderText };
    }
    return merged;
  }

  let firstDeltaSeq = 0;
  for (const event of events) {
    if (event.name === 'message.delta' && !isTeamSurfacePayload(event.data)) {
      firstDeltaSeq = event.seq;
      break;
    }
  }

  const assistant: ChatItem = {
    id: `assistant-leader-${firstDeltaSeq || Date.now()}`,
    kind: 'assistant',
    text: leaderText,
    seq: firstDeltaSeq || undefined,
  };

  const userIndex = lastUserIndex(merged);
  let insertAt = merged.length;
  for (let index = userIndex + 1; index < merged.length; index += 1) {
    const seq = chatItemOrderKey(merged[index]);
    if (seq > (assistant.seq ?? 0)) {
      insertAt = index;
      break;
    }
  }
  merged.splice(insertAt, 0, assistant);
  return merged;
}
