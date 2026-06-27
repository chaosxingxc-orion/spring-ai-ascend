import { listSessionMessages } from '../api/client';
import type { ChatItem } from '../types/events';
import { sortChatItemsByOrder } from './chatItemOrder';
import { normalizeToolPayload, parseChatItems } from './eventPayload';

export type MemberDelegationChatItem = Extract<ChatItem, { kind: 'member-delegation' }>;

export interface DelegationInput {
  description?: string;
  message?: string;
  /** True when only a clipped preview is available until persisted args are loaded. */
  truncated?: boolean;
}

function isDelegationSendTool(toolName: string): boolean {
  const normalized = toolName.toLowerCase();
  return normalized.includes('send_message') && normalized.includes('team');
}

function readRedactable(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) {
    return value.trim();
  }
  if (value && typeof value === 'object') {
    const preview = (value as Record<string, unknown>).preview;
    if (typeof preview === 'string' && preview.trim()) {
      return preview.trim();
    }
  }
  return undefined;
}

function redactedByteLength(value: unknown): number | undefined {
  if (!value || typeof value !== 'object') {
    return undefined;
  }
  const bytes = (value as Record<string, unknown>).bytes;
  return typeof bytes === 'number' ? bytes : undefined;
}

function isClippedPreview(text: string, args: Record<string, unknown>): boolean {
  if (text.endsWith('…') || text.endsWith('...')) {
    return true;
  }
  const preview = readRedactable(args.messagePreview);
  if (!preview || text !== preview) {
    return false;
  }
  return preview.endsWith('…') || preview.endsWith('...');
}

function readDelegationMessage(args: Record<string, unknown>): { text?: string; truncated: boolean } {
  const rawMessage = args.message;
  if (typeof rawMessage === 'string' && rawMessage.trim()) {
    const text = rawMessage.trim();
    return { text, truncated: isClippedPreview(text, args) };
  }
  if (rawMessage && typeof rawMessage === 'object') {
    const text = readRedactable(rawMessage);
    const bytes = redactedByteLength(rawMessage) ?? 0;
    const previewBytes = text ? new TextEncoder().encode(text).length : 0;
    if (text) {
      return { text, truncated: bytes > previewBytes };
    }
  }
  const previewOnly = readRedactable(args.messagePreview);
  if (previewOnly) {
    return { text: previewOnly, truncated: isClippedPreview(previewOnly, args) };
  }
  return { truncated: false };
}

function stripMention(value?: string): string | undefined {
  return value ? value.replace(/^@/, '') : value;
}

function delegationTarget(args: Record<string, unknown>): string | undefined {
  const routing = args.routing && typeof args.routing === 'object'
    ? (args.routing as Record<string, unknown>)
    : null;
  return stripMention(
    readRedactable(args.memberId)
    ?? readRedactable(args.to)
    ?? readRedactable(args.recipient)
    ?? readRedactable(args.target)
    ?? readRedactable(routing?.target),
  );
}

function matchesDelegationMember(
  memberId: string,
  memberName: string | undefined,
  targetId?: string,
  targetName?: string,
): boolean {
  if (targetId && targetId === memberId) {
    return true;
  }
  return Boolean(memberName && targetName && memberName === targetName);
}

function delegationFromArgs(args: Record<string, unknown>): DelegationInput | null {
  const { text: message, truncated } = readDelegationMessage(args);
  const description = readRedactable(args.description);
  if (!message && !description) {
    return null;
  }
  return {
    message: message ?? description,
    description: message && description && message !== description ? description : undefined,
    truncated,
  };
}

function delegationFromRow(
  row: Record<string, unknown>,
  memberId: string,
  memberName?: string,
): DelegationInput | null {
  const kind = typeof row.kind === 'string' ? row.kind : '';
  if (kind === 'delegation') {
    const targetId = stripMention(readRedactable(row.memberId));
    const targetName = readRedactable(row.memberName);
    if (!matchesDelegationMember(memberId, memberName, targetId, targetName)) {
      return null;
    }
    const message = readRedactable(row.message);
    const description = readRedactable(row.description);
    if (!message && !description) {
      return null;
    }
    return {
      message: message ?? description,
      description: message && description && message !== description ? description : undefined,
      truncated: false,
    };
  }
  if (kind !== 'tool') {
    return null;
  }
  const toolName = typeof row.toolName === 'string' ? row.toolName : '';
  if (!isDelegationSendTool(toolName)) {
    return null;
  }
  const args = normalizeToolPayload(row.args);
  if (!args || typeof args !== 'object') {
    return null;
  }
  const record = args as Record<string, unknown>;
  if (!matchesDelegationMember(memberId, memberName, delegationTarget(record), readRedactable(record.memberName))) {
    return null;
  }
  return delegationFromArgs(record);
}

export function enrichMemberDelegationItems(
  items: ChatItem[],
  rawMessages: unknown[],
): ChatItem[] {
  const fullById = new Map<string, { message?: string; description?: string }>();
  for (const raw of rawMessages) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const row = raw as Record<string, unknown>;
    if (row.kind !== 'delegation') {
      continue;
    }
    const message = readRedactable(row.message);
    const description = readRedactable(row.description);
    if (!message && !description) {
      continue;
    }
    const id = readRedactable(row.toolCallId)
      ?? (typeof row.id === 'string' ? row.id : undefined)
      ?? `delegation-${readRowSeq(row)}`;
    fullById.set(id, { message: message ?? description, description });
  }
  if (fullById.size === 0) {
    return items;
  }
  return items.map((item) => {
    if (item.kind !== 'member-delegation' || !item.truncated) {
      return item;
    }
    const full = fullById.get(item.id);
    if (!full?.message) {
      return item;
    }
    if (full.message.length <= (item.message?.length ?? 0) && !item.message?.endsWith('…')) {
      return item;
    }
    return {
      ...item,
      message: full.message,
      description: full.description && full.description !== full.message
        ? full.description
        : item.description,
      truncated: false,
    };
  });
}

function readRowSeq(row: Record<string, unknown>): number {
  return typeof row.seq === 'number' ? row.seq : 0;
}

function pickBetterDelegation(
  current: DelegationInput | null,
  next: DelegationInput | null,
  currentSeq: number,
  nextSeq: number,
): { input: DelegationInput | null; seq: number } {
  if (!next?.message && !next?.description) {
    return { input: current, seq: currentSeq };
  }
  if (!current?.message && !current?.description) {
    return { input: next, seq: nextSeq };
  }
  // Member re-tasked: prefer the newer delegation card even when the body is shorter.
  if (nextSeq > currentSeq) {
    if (current.truncated && !next.truncated) {
      return { input: next, seq: nextSeq };
    }
    return { input: next, seq: nextSeq };
  }
  if (nextSeq < currentSeq) {
    if (current.truncated && !next.truncated) {
      return { input: next, seq: nextSeq };
    }
    return { input: current, seq: currentSeq };
  }
  const currentLen = current?.message?.length ?? 0;
  const nextLen = next?.message?.length ?? 0;
  if (nextLen > currentLen || (current.truncated && !next.truncated)) {
    return { input: next, seq: nextSeq };
  }
  if (nextLen < currentLen || (!current.truncated && next.truncated)) {
    return { input: current, seq: currentSeq };
  }
  return { input: next, seq: nextSeq };
}

/** Scan raw session_messages rows for full delegation prompts (kind=delegation or tool args). */
export function delegationInputFromRawMessages(
  rawMessages: unknown[],
  memberId: string,
  memberName?: string,
): DelegationInput | null {
  let best: DelegationInput | null = null;
  let bestSeq = -1;
  for (const raw of rawMessages) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const row = raw as Record<string, unknown>;
    const next = delegationFromRow(row, memberId, memberName);
    const picked = pickBetterDelegation(best, next, bestSeq, readRowSeq(row));
    best = picked.input;
    bestSeq = picked.seq;
  }
  return best;
}

function delegationToolsMerged(chatItems: ChatItem[], persisted: ChatItem[]): ChatItem[] {
  const eventTools = chatItems.filter(
    (item): item is Extract<ChatItem, { kind: 'tool' }> =>
      item.kind === 'tool' && isDelegationSendTool(item.toolName),
  );
  const enriched = enrichDelegationToolItems(eventTools, persisted);
  const seen = new Set(enriched.map((item) => item.toolCallId).filter(Boolean));
  for (const item of persisted) {
    if (
      item.kind === 'tool'
      && isDelegationSendTool(item.toolName)
      && item.toolCallId
      && !seen.has(item.toolCallId)
    ) {
      enriched.push(item);
      seen.add(item.toolCallId);
    }
  }
  return enriched;
}

/**
 * Pull the task body/description the leader delegated to a member out of that member's
 * `team.send_message` card args. Returns the **latest** delegation (members may be re-tasked).
 */
export function delegationInputForMember(items: ChatItem[], memberId: string): DelegationInput | null {
  const cards = delegationCardsForMember(items, memberId);
  if (cards.length === 0) {
    return null;
  }
  const latest = cards[cards.length - 1];
  return delegationInputFromCard(latest);
}

function delegationCardFromToolItem(
  item: Extract<ChatItem, { kind: 'tool' }>,
  memberId: string,
): MemberDelegationChatItem | null {
  const args = item.args && typeof item.args === 'object' ? (item.args as Record<string, unknown>) : null;
  if (!args || delegationTarget(args) !== memberId) {
    return null;
  }
  const input = delegationFromArgs(args);
  if (!input?.message && !input?.description) {
    return null;
  }
  return {
    id: item.toolCallId ?? item.id,
    kind: 'member-delegation',
    message: input.message,
    description: input.description,
    truncated: input.truncated,
    round: 0,
    reawaken: args.reawaken === true,
    seq: item.seq ?? item.startedAt ?? 0,
  };
}

/** Every `team.send_message` to this member becomes its own timeline card (chronological). */
export function delegationCardsForMember(items: ChatItem[], memberId: string): MemberDelegationChatItem[] {
  const cards: MemberDelegationChatItem[] = [];
  for (const item of items) {
    if (item.kind !== 'tool' || !isDelegationSendTool(item.toolName)) {
      continue;
    }
    const card = delegationCardFromToolItem(item, memberId);
    if (card) {
      cards.push(card);
    }
  }
  return cards
    .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
    .map((card, index) => ({ ...card, round: index + 1 }));
}

function delegationCardFromRawRow(
  row: Record<string, unknown>,
  memberId: string,
  memberName?: string,
): MemberDelegationChatItem | null {
  const kind = typeof row.kind === 'string' ? row.kind : '';
  if (kind === 'delegation') {
    const targetId = stripMention(readRedactable(row.memberId));
    const targetName = readRedactable(row.memberName);
    if (!matchesDelegationMember(memberId, memberName, targetId, targetName)) {
      return null;
    }
    const message = readRedactable(row.message);
    const description = readRedactable(row.description);
    if (!message && !description) {
      return null;
    }
    const toolCallId = readRedactable(row.toolCallId);
    return {
      id: toolCallId ?? `delegation-${readRowSeq(row)}`,
      kind: 'member-delegation',
      message: message ?? description,
      description: message && description && message !== description ? description : undefined,
      truncated: false,
      round: 0,
      seq: readRowSeq(row),
    };
  }
  if (kind !== 'tool') {
    return null;
  }
  const toolName = typeof row.toolName === 'string' ? row.toolName : '';
  if (!isDelegationSendTool(toolName)) {
    return null;
  }
  const args = normalizeToolPayload(row.args);
  if (!args || typeof args !== 'object') {
    return null;
  }
  const record = args as Record<string, unknown>;
  if (!matchesDelegationMember(memberId, memberName, delegationTarget(record), readRedactable(record.memberName))) {
    return null;
  }
  const input = delegationFromArgs(record);
  if (!input?.message && !input?.description) {
    return null;
  }
  const toolCallId = readRedactable(row.toolCallId);
  return {
    id: toolCallId ?? `delegation-tool-${readRowSeq(row)}`,
    kind: 'member-delegation',
    message: input.message,
    description: input.description,
    truncated: input.truncated,
    round: 0,
    reawaken: record.reawaken === true,
    seq: readRowSeq(row),
  };
}

function delegationCardsFromRawMessages(
  rawMessages: unknown[],
  memberId: string,
  memberName?: string,
): MemberDelegationChatItem[] {
  const cards: MemberDelegationChatItem[] = [];
  for (const raw of rawMessages) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const card = delegationCardFromRawRow(raw as Record<string, unknown>, memberId, memberName);
    if (card) {
      cards.push(card);
    }
  }
  return cards
    .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
    .map((card, index) => ({ ...card, round: index + 1 }));
}

function mergeDelegationCard(
  current: MemberDelegationChatItem,
  next: MemberDelegationChatItem,
): MemberDelegationChatItem {
  const currentLen = current.message?.length ?? 0;
  const nextLen = next.message?.length ?? 0;
  if (nextLen > currentLen || (current.truncated && !next.truncated)) {
    return {
      ...next,
      round: current.round,
      reawaken: next.reawaken ?? current.reawaken,
    };
  }
  return current;
}

export function mergeDelegationCardsById(
  ...lists: MemberDelegationChatItem[][]
): MemberDelegationChatItem[] {
  const byId = new Map<string, MemberDelegationChatItem>();
  for (const list of lists) {
    for (const card of list) {
      const existing = byId.get(card.id);
      byId.set(card.id, existing ? mergeDelegationCard(existing, card) : card);
    }
  }
  return [...byId.values()]
    .sort((a, b) => (a.seq ?? 0) - (b.seq ?? 0))
    .map((card, index) => ({ ...card, round: index + 1 }));
}

export function delegationInputFromCard(card: MemberDelegationChatItem): DelegationInput {
  return {
    message: card.message,
    description: card.description,
    truncated: card.truncated,
  };
}

/** Insert delegation cards into the member-scoped timeline at their run-event seq. */
export function mergeMemberTimeline(
  memberItems: ChatItem[],
  delegations: MemberDelegationChatItem[],
): ChatItem[] {
  if (delegations.length === 0) {
    return memberItems;
  }
  return sortChatItemsByOrder([...memberItems, ...delegations]);
}

export function mergeDelegationInputs(
  ...inputs: Array<DelegationInput | null | undefined>
): DelegationInput | null {
  let best: DelegationInput | null = null;
  for (const input of inputs) {
    if (!input?.message && !input?.description) {
      continue;
    }
    if (
      !best
      || (input.message?.length ?? 0) > (best.message?.length ?? 0)
      || (best.truncated && !input.truncated)
    ) {
      best = input;
    }
  }
  return best;
}

/** Prefer full delegation args from session_messages over redacted run_event payloads. */
export function enrichDelegationToolItems(eventItems: ChatItem[], messages: ChatItem[]): ChatItem[] {
  const persistedArgs = new Map<string, unknown>();
  for (const item of messages) {
    if (item.kind !== 'tool' || !item.toolCallId || !isDelegationSendTool(item.toolName) || item.args == null) {
      continue;
    }
    const args = item.args;
    const existing = persistedArgs.get(item.toolCallId);
    if (existing == null || delegationMessageLength(args) > delegationMessageLength(existing)) {
      persistedArgs.set(item.toolCallId, args);
    }
  }
  if (persistedArgs.size === 0) {
    return eventItems;
  }
  return eventItems.map((item) => {
    if (item.kind !== 'tool' || !item.toolCallId || !isDelegationSendTool(item.toolName)) {
      return item;
    }
    const args = persistedArgs.get(item.toolCallId);
    return args != null ? { ...item, args } : item;
  });
}

function delegationMessageLength(args: unknown): number {
  if (!args || typeof args !== 'object') {
    return 0;
  }
  const { text } = readDelegationMessage(args as Record<string, unknown>);
  return text?.length ?? 0;
}

export async function loadDelegationCardsForMember(
  sessionId: string,
  memberId: string,
  chatItems: ChatItem[],
  memberName?: string,
): Promise<MemberDelegationChatItem[]> {
  const fromChat = delegationCardsForMember(chatItems, memberId);
  try {
    const raw = await listSessionMessages(sessionId);
    const fromRaw = delegationCardsFromRawMessages(raw, memberId, memberName);
    const persisted = parseChatItems(raw);
    const tools = delegationToolsMerged(chatItems, persisted);
    const fromDb = delegationCardsForMember(tools, memberId);
    return mergeDelegationCardsById(fromChat, fromDb, fromRaw);
  } catch {
    return fromChat;
  }
}

export async function loadDelegationInputForMember(
  sessionId: string,
  memberId: string,
  chatItems: ChatItem[],
  memberName?: string,
): Promise<DelegationInput | null> {
  const cards = await loadDelegationCardsForMember(sessionId, memberId, chatItems, memberName);
  if (cards.length === 0) {
    return null;
  }
  return delegationInputFromCard(cards[cards.length - 1]!);
}

export function sameDelegationCards(
  a: MemberDelegationChatItem[],
  b: MemberDelegationChatItem[],
): boolean {
  if (a.length !== b.length) {
    return false;
  }
  return a.every((card, index) => {
    const other = b[index];
    return other != null
      && card.id === other.id
      && card.message === other.message
      && card.description === other.description
      && card.truncated === other.truncated
      && card.round === other.round;
  });
}

export function sameDelegationInput(a: DelegationInput | null, b: DelegationInput | null): boolean {
  if (a == null || b == null) {
    return a === b;
  }
  return a.message === b.message
    && a.description === b.description
    && a.truncated === b.truncated;
}
