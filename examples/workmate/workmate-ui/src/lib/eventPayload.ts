import type {
  ApprovalRequiredPayload,
  ArtifactAddedPayload,
  ChatItem,
  PlanPayload,
  QuestionRequiredPayload,
  ToolStatus,
  UsageDeltaPayload,
} from '../types/events';
import { normalizeToolStatus, toolEndStatus } from './toolStatus';
import { parseMentionsFromServer } from './mentionParse';
import { parseUserAttachments } from './userAttachments';

function isRecord(value: unknown): value is Record<string, unknown> {
  return value != null && typeof value === 'object' && !Array.isArray(value);
}

function readString(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined;
}

/** 服务端 session_messages 可能将 args/result 存为 JSON 字符串。 */
export function normalizeToolPayload(value: unknown): unknown {
  if (typeof value !== 'string') {
    return value;
  }
  const trimmed = value.trim();
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
    return value;
  }
  try {
    return JSON.parse(trimmed);
  } catch {
    return value;
  }
}

/**
 * 读取可能被审计脱敏器截断为 `{ preview, bytes }` 的文本字段（W22 RunEventPayloadRedactor）。
 * 持久化 run_events / resume 广播会把 `text` 截断成对象；实时首发 SSE 仍是完整字符串。
 */
export function readRedactableText(value: unknown): string | undefined {
  const direct = readString(value);
  if (direct != null) {
    return direct;
  }
  if (isRecord(value)) {
    const preview = readString(value.preview);
    if (preview != null) {
      return preview;
    }
  }
  return undefined;
}

export function parseMessageDelta(data: unknown): { text: string; messageId?: string } {
  if (!isRecord(data)) {
    return { text: '' };
  }
  return {
    text:
      readRedactableText(data.text)
      ?? readRedactableText(data.delta)
      ?? readRedactableText(data.content)
      ?? '',
    messageId: readString(data.messageId) ?? readString(data.message_id),
  };
}

export function parseToolStart(data: unknown): {
  toolName: string;
  toolCallId?: string;
  args?: unknown;
} {
  if (!isRecord(data)) {
    return { toolName: '' };
  }
  return {
    toolName: readString(data.toolName) ?? '',
    toolCallId: readString(data.toolCallId),
    args: normalizeToolPayload(data.args),
  };
}

export function parseToolEnd(data: unknown): {
  toolName: string;
  toolCallId?: string;
  result: unknown;
  status: ToolStatus;
} {
  if (!isRecord(data)) {
    return { toolName: '', result: undefined, status: 'success' };
  }
  const result = normalizeToolPayload(data.result);
  return {
    toolName: readString(data.toolName) ?? '',
    toolCallId: readString(data.toolCallId),
    result,
    status: toolEndStatus(isToolFailureResult(result)),
  };
}

function isToolFailureResult(result: unknown): boolean {
  if (result == null || typeof result !== 'object') {
    return false;
  }
  const record = result as Record<string, unknown>;
  if (record.success === false) {
    return true;
  }
  if (record.data != null && typeof record.data === 'object') {
    return (record.data as Record<string, unknown>).success === false;
  }
  return false;
}

export function parseApprovalRequired(data: unknown): ApprovalRequiredPayload | null {
  if (!isRecord(data)) {
    return null;
  }
  const approvalId = readString(data.approvalId);
  const sessionId = readString(data.sessionId);
  const tool = readString(data.tool);
  if (!approvalId || !sessionId || !tool) {
    return null;
  }
  return {
    approvalId,
    sessionId,
    tool,
    toolCallId: readString(data.toolCallId),
    risk: readString(data.risk) ?? '',
    reason: readString(data.reason) ?? '',
    summary: readString(data.summary) ?? '',
    args: isRecord(data.args) ? data.args : {},
  };
}

export function parseQuestionRequired(data: unknown): QuestionRequiredPayload | null {
  if (!isRecord(data)) {
    return null;
  }
  const questionId = readString(data.questionId);
  const sessionId = readString(data.sessionId);
  const question = readString(data.question);
  if (!questionId || !sessionId || !question) {
    return null;
  }
  const options = Array.isArray(data.options)
    ? data.options.filter((entry): entry is string => typeof entry === 'string')
    : [];
  const messageId = readString(data.messageId) ?? readString(data.message_id);
  return {
    questionId,
    sessionId,
    question,
    options,
    allowFreeText: data.allowFreeText !== false,
    multiSelect: data.multiSelect === true,
    toolName: readString(data.toolName),
    messageId,
  };
}

export function parseExpertSwitched(data: unknown): Extract<ChatItem, { kind: 'expert-switched' }> | null {
  if (!isRecord(data)) {
    return null;
  }
  const toExpertId = readString(data.toExpertId);
  const toExpertName = readString(data.toExpertName);
  if (!toExpertId || !toExpertName) {
    return null;
  }
  const messageId = readString(data.messageId) ?? `expert-switch-${toExpertId}-${readString(data.newGeneration) ?? '0'}`;
  return {
    id: messageId,
    kind: 'expert-switched',
    fromExpertId: readString(data.fromExpertId),
    toExpertId,
    fromExpertName: readString(data.fromExpertName),
    toExpertName,
    newGeneration: typeof data.newGeneration === 'number' ? data.newGeneration : undefined,
    mode: readString(data.mode),
  };
}

export function parseArtifactAdded(data: unknown): ArtifactAddedPayload | null {
  if (!isRecord(data)) {
    return null;
  }
  const path = readString(data.path);
  const name = readString(data.name);
  const mime = readString(data.mime);
  const updatedAt = readString(data.updatedAt);
  if (!path || !name || !mime || !updatedAt) {
    return null;
  }
  const preferredTab = readString(data.preferredTab);
  return {
    path,
    name,
    mime,
    size: typeof data.size === 'number' ? data.size : 0,
    updatedAt,
    openInPanel: data.openInPanel === true,
    preferredTab:
      preferredTab === 'browser' || preferredTab === 'source' || preferredTab === 'changes'
        ? preferredTab
        : undefined,
  };
}

export function parsePlanCreate(data: unknown): PlanPayload | null {
  if (!isRecord(data) || !readString(data.planId) || !Array.isArray(data.steps)) {
    return null;
  }
  const steps = data.steps
    .filter(isRecord)
    .map((step) => ({
      id: readString(step.id) ?? '',
      title: readString(step.title) ?? '',
      status: readString(step.status) as PlanPayload['steps'][number]['status'],
    }))
    .filter((step) => step.id && step.title);
  if (steps.length === 0) {
    return null;
  }
  return {
    planId: readString(data.planId)!,
    title: readString(data.title),
    steps,
  };
}

export function parseUsageDelta(data: unknown): UsageDeltaPayload | null {
  if (!isRecord(data)) {
    return null;
  }
  if (typeof data.totalPromptTokens !== 'number' || typeof data.totalCompletionTokens !== 'number') {
    return null;
  }
  return {
    deltaPromptTokens: typeof data.deltaPromptTokens === 'number' ? data.deltaPromptTokens : undefined,
    deltaCompletionTokens:
      typeof data.deltaCompletionTokens === 'number' ? data.deltaCompletionTokens : undefined,
    totalPromptTokens: data.totalPromptTokens,
    totalCompletionTokens: data.totalCompletionTokens,
    model: readString(data.model),
    memberId: readString(data.memberId),
  };
}

export function parseRunError(data: unknown): string {
  if (!isRecord(data)) {
    return 'Run failed';
  }
  return readString(data.message) ?? 'Run failed';
}

export function parseTeamMemberEvent(data: unknown): {
  memberId: string;
  memberName?: string;
  role?: string;
  order?: number;
  avatar?: string;
  subRunId?: string;
  parentRunId?: string;
  summary?: string;
  error?: string;
  promptTokens?: number;
  completionTokens?: number;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const memberId = readString(data.memberId);
  if (!memberId) {
    return null;
  }
  return {
    memberId,
    memberName: readString(data.memberName),
    role: readString(data.role),
    order: typeof data.order === 'number' ? data.order : undefined,
    avatar: readString(data.avatar),
    subRunId: readString(data.subRunId),
    parentRunId: readString(data.parentRunId),
    summary: readString(data.summary),
    error: readString(data.error),
    promptTokens: typeof data.promptTokens === 'number' ? data.promptTokens : undefined,
    completionTokens: typeof data.completionTokens === 'number' ? data.completionTokens : undefined,
  };
}

export function parseTeamStarted(data: unknown): {
  teamId: string;
  parentRunId?: string;
  memberCount?: number;
  collaboration?: 'sequential' | 'parallel';
  pattern?: import('../types/events').CoordinationPattern;
  maxIterations?: number;
  convergenceTarget?: number;
  busMode?: string;
  topicBusProvider?: string;
  lead?: { name?: string; title?: string; avatar?: string };
  members?: { memberId: string; memberName?: string; order?: number; role?: string; avatar?: string; participantRole?: string }[];
  teamRuntime?: string;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const teamId = readString(data.teamId);
  if (!teamId) {
    return null;
  }
  const collaboration = readString(data.collaboration);
  const pattern = readString(data.pattern);
  const lead = isRecord(data.lead)
    ? {
        name: readString(data.lead.name),
        title: readString(data.lead.title),
        avatar: readString(data.lead.avatar),
      }
    : undefined;
  const members = Array.isArray(data.members)
    ? data.members.filter(isRecord).map((entry) => ({
        memberId: readString(entry.memberId) ?? '',
        memberName: readString(entry.memberName),
        order: typeof entry.order === 'number' ? entry.order : undefined,
        role: readString(entry.role),
        avatar: readString(entry.avatar),
        participantRole: readString(entry.participantRole),
      })).filter((entry) => entry.memberId)
    : undefined;
  const knownPatterns = ['orchestrator', 'pipeline', 'agent-team', 'generator-verifier', 'message-bus', 'shared-state'];
  return {
    teamId,
    parentRunId: readString(data.parentRunId),
    memberCount: typeof data.memberCount === 'number' ? data.memberCount : undefined,
    maxIterations: typeof data.maxIterations === 'number' ? data.maxIterations : undefined,
    convergenceTarget: typeof data.convergenceTarget === 'number' ? data.convergenceTarget : undefined,
    busMode: readString(data.busMode),
    topicBusProvider: readString(data.topicBusProvider),
    collaboration: collaboration === 'parallel' ? 'parallel' : collaboration === 'sequential' ? 'sequential' : undefined,
    pattern: pattern && knownPatterns.includes(pattern)
      ? (pattern as import('../types/events').CoordinationPattern)
      : undefined,
    lead,
    members,
    teamRuntime: readString(data.teamRuntime),
  };
}

export function parseTeamBuildCompleted(data: unknown): {
  teamName?: string;
  displayName?: string;
  memberCount?: number;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  return {
    teamName: readString(data.teamName) ?? readString(data.team_name),
    displayName: readString(data.displayName) ?? readString(data.display_name),
    memberCount: typeof data.memberCount === 'number' ? data.memberCount : undefined,
  };
}

export function parseTeamPhase(data: unknown): { phase: number; label?: string } | null {
  if (!isRecord(data)) {
    return null;
  }
  const phase = typeof data.phase === 'number' ? data.phase : undefined;
  if (phase == null || phase < 1) {
    return null;
  }
  return {
    phase,
    label: readString(data.label),
  };
}

export function parseTeamMemory(data: unknown): {
  parentRunId?: string;
  path: string;
  section: string;
  preview?: string;
  totalBytes?: number;
  action?: string;
  version?: number;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const path = readString(data.path);
  const section = readString(data.section);
  if (!path || !section) {
    return null;
  }
  return {
    parentRunId: readString(data.parentRunId),
    path,
    section,
    preview: readString(data.preview),
    totalBytes: typeof data.totalBytes === 'number' ? data.totalBytes : undefined,
    action: readString(data.action),
    version: typeof data.version === 'number' ? data.version : undefined,
  };
}

export function parseTeamBusSubscribed(data: unknown): {
  subscriberMemberId: string;
  topics: string[];
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const subscriberMemberId = readString(data.subscriberMemberId);
  if (!subscriberMemberId) {
    return null;
  }
  const topics = Array.isArray(data.topics)
    ? data.topics.filter((t): t is string => typeof t === 'string')
    : [];
  return { subscriberMemberId, topics };
}

export function parseTeamBusPublished(data: unknown): {
  parentRunId?: string;
  topic: string;
  authorMemberId?: string;
  authorMemberName?: string;
  preview?: string;
  publishSource?: 'orchestrator' | 'mid-run' | 'outcome';
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const topic = readString(data.topic);
  if (!topic) {
    return null;
  }
  const publishSource = readString(data.publishSource);
  const knownSources = ['orchestrator', 'mid-run', 'outcome'] as const;
  return {
    parentRunId: readString(data.parentRunId),
    topic,
    authorMemberId: readString(data.authorMemberId),
    authorMemberName: readString(data.authorMemberName),
    preview: readString(data.preview),
    publishSource:
      publishSource && knownSources.includes(publishSource as typeof knownSources[number])
        ? (publishSource as typeof knownSources[number])
        : undefined,
  };
}

export function parseTeamCompleted(data: unknown): {
  busEntryCount?: number;
  busMode?: string;
  topicBusProvider?: string;
  converged?: boolean;
  blackboardVersion?: number;
  convergenceStreak?: number;
  iterationsCompleted?: number;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  return {
    busEntryCount: typeof data.busEntryCount === 'number' ? data.busEntryCount : undefined,
    busMode: readString(data.busMode),
    topicBusProvider: readString(data.topicBusProvider),
    converged: typeof data.converged === 'boolean' ? data.converged : undefined,
    blackboardVersion: typeof data.blackboardVersion === 'number' ? data.blackboardVersion : undefined,
    convergenceStreak: typeof data.convergenceStreak === 'number' ? data.convergenceStreak : undefined,
    iterationsCompleted: typeof data.iterationsCompleted === 'number' ? data.iterationsCompleted : undefined,
  };
}

export function parseTeamStateProgress(data: unknown): {
  iteration: number;
  maxIterations: number;
  convergenceStreak: number;
  convergenceTarget: number;
  blackboardVersion?: number;
  roundHadNewFindings?: boolean;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  if (typeof data.iteration !== 'number' || typeof data.maxIterations !== 'number') {
    return null;
  }
  return {
    iteration: data.iteration,
    maxIterations: data.maxIterations,
    convergenceStreak: typeof data.convergenceStreak === 'number' ? data.convergenceStreak : 0,
    convergenceTarget: typeof data.convergenceTarget === 'number' ? data.convergenceTarget : 0,
    blackboardVersion: typeof data.blackboardVersion === 'number' ? data.blackboardVersion : undefined,
    roundHadNewFindings: typeof data.roundHadNewFindings === 'boolean' ? data.roundHadNewFindings : undefined,
  };
}

export function parseTeamIteration(data: unknown): {
  iteration: number;
  maxIterations: number;
  teamId?: string;
  parentRunId?: string;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  if (typeof data.iteration !== 'number' || typeof data.maxIterations !== 'number') {
    return null;
  }
  return {
    iteration: data.iteration,
    maxIterations: data.maxIterations,
    teamId: readString(data.teamId),
    parentRunId: readString(data.parentRunId),
  };
}

export function parseTeamVerify(data: unknown): {
  memberId: string;
  memberName?: string;
  role?: string;
  order?: number;
  avatar?: string;
  participantRole?: string;
  subRunId?: string;
  parentRunId?: string;
  iteration?: number;
  maxIterations?: number;
  summary?: string;
  feedback?: string;
  programmatic?: boolean;
  error?: string;
} | null {
  if (!isRecord(data)) {
    return null;
  }
  const memberId = readString(data.memberId);
  if (!memberId) {
    return null;
  }
  return {
    memberId,
    memberName: readString(data.memberName),
    role: readString(data.role),
    order: typeof data.order === 'number' ? data.order : undefined,
    avatar: readString(data.avatar),
    participantRole: readString(data.participantRole),
    subRunId: readString(data.subRunId),
    parentRunId: readString(data.parentRunId),
    iteration: typeof data.iteration === 'number' ? data.iteration : undefined,
    maxIterations: typeof data.maxIterations === 'number' ? data.maxIterations : undefined,
    summary: readString(data.summary),
    feedback: readString(data.feedback),
    programmatic: typeof data.programmatic === 'boolean' ? data.programmatic : undefined,
    error: readString(data.error),
  };
}

function parseToolItem(raw: Record<string, unknown>): ChatItem | null {
  if (readString(raw.surface) === 'team') {
    return null;
  }
  const toolName = readString(raw.toolName);
  const status = normalizeToolStatus(readString(raw.status));
  if (!toolName || !status) {
    return null;
  }
  return {
    id: readString(raw.id) ?? crypto.randomUUID(),
    kind: 'tool',
    toolName,
    toolCallId: readString(raw.toolCallId),
    status,
    args: normalizeToolPayload(raw.args),
    result: normalizeToolPayload(raw.result),
    seq: readSeq(raw),
    startedAt: readSeq(raw),
  };
}

function readSeq(raw: Record<string, unknown>): number | undefined {
  return typeof raw.seq === 'number' ? raw.seq : undefined;
}

/** W36-A5 — 团队成员子 Run 事件不应进入主对话投影。 */
export { isTeamSurfacePayload } from './memberEventProjection';

export function parseChatItem(raw: unknown): ChatItem | null {
  if (!isRecord(raw) || !readString(raw.kind) || !readString(raw.id)) {
    return null;
  }
  if (readString(raw.surface) === 'team') {
    return null;
  }
  switch (raw.kind) {
    case 'user': {
      const attachments = parseUserAttachments(raw.attachments);
      return {
        id: raw.id as string,
        kind: 'user',
        text: readString(raw.text) ?? '',
        seq: readSeq(raw),
        mentions: parseMentionsFromServer(raw.mentions),
        attachments: attachments.length > 0 ? attachments : undefined,
      };
    }
    case 'assistant':
      return { id: raw.id as string, kind: 'assistant', text: readString(raw.text) ?? '', seq: readSeq(raw) };
    case 'tool':
      return parseToolItem(raw);
    case 'system':
      return {
        id: raw.id as string,
        kind: 'system',
        text: readString(raw.text) ?? '',
        tone: raw.tone === 'error' || raw.tone === 'info' ? raw.tone : undefined,
        seq: readSeq(raw),
      };
    case 'expert-switched': {
      const toExpertId = readString(raw.toExpertId);
      const toExpertName = readString(raw.toExpertName);
      if (!toExpertId || !toExpertName) {
        return null;
      }
      return {
        id: raw.id as string,
        kind: 'expert-switched',
        fromExpertId: readString(raw.fromExpertId),
        toExpertId,
        fromExpertName: readString(raw.fromExpertName),
        toExpertName,
        newGeneration: typeof raw.newGeneration === 'number' ? raw.newGeneration : undefined,
        mode: readString(raw.mode),
        seq: readSeq(raw),
      };
    }
    case 'approval': {
      const approvalId = readString(raw.approvalId);
      const tool = readString(raw.tool);
      if (!approvalId || !tool) {
        return null;
      }
      const status = readString(raw.status);
      return {
        id: raw.id as string,
        kind: 'approval',
        approvalId,
        tool,
        risk: readString(raw.risk) ?? '',
        reason: readString(raw.reason) ?? '',
        summary: readString(raw.summary) ?? '',
        args: isRecord(raw.args) ? raw.args : {},
        status:
          status === 'approved' || status === 'denied' || status === 'pending'
            ? status
            : 'pending',
        seq: readSeq(raw),
      };
    }
    case 'question': {
      const questionId = readString(raw.questionId);
      const question = readString(raw.question);
      if (!questionId || !question) {
        return null;
      }
      const options = Array.isArray(raw.options)
        ? raw.options.filter((entry): entry is string => typeof entry === 'string')
        : [];
      const selections = Array.isArray(raw.selections)
        ? raw.selections.filter((entry): entry is string => typeof entry === 'string')
        : undefined;
      const answerText = readString(raw.answerText);
      let status =
        readString(raw.status) === 'answered'
        || readString(raw.status) === 'skipped'
        || readString(raw.status) === 'cancelled'
        || readString(raw.status) === 'pending'
          ? readString(raw.status)
          : 'pending';
      if (status === 'cancelled' && ((selections?.length ?? 0) > 0 || answerText)) {
        status = 'answered';
      }
      return {
        id: raw.id as string,
        kind: 'question',
        questionId,
        question,
        options,
        allowFreeText: raw.allowFreeText !== false,
        multiSelect: raw.multiSelect === true,
        status:
          status === 'answered' || status === 'skipped' || status === 'cancelled' || status === 'pending'
            ? status
            : 'pending',
        selections,
        answerText,
        seq: readSeq(raw),
      };
    }
    case 'plan': {
      const planId = readString(raw.planId);
      if (!planId || !Array.isArray(raw.steps)) {
        return null;
      }
      const steps = raw.steps
        .filter(isRecord)
        .map((step) => ({
          id: readString(step.id) ?? '',
          title: readString(step.title) ?? '',
          status: readString(step.status) as PlanPayload['steps'][number]['status'],
        }))
        .filter((step) => step.id && step.title);
      if (steps.length === 0) {
        return null;
      }
      return {
        id: raw.id as string,
        kind: 'plan',
        planId,
        title: readString(raw.title),
        steps,
        confirmed: raw.confirmed === true,
      };
    }
    case 'artifact-cta': {
      const path = readString(raw.path);
      const name = readString(raw.name);
      const mime = readString(raw.mime);
      if (!path || !name || !mime) {
        return null;
      }
      const preferredTab = readString(raw.preferredTab);
      return {
        id: raw.id as string,
        kind: 'artifact-cta',
        path,
        name,
        mime,
        preferredTab:
          preferredTab === 'browser' || preferredTab === 'source' || preferredTab === 'changes'
            ? preferredTab
            : undefined,
      };
    }
    default:
      return null;
  }
}

export function parseChatItems(raw: unknown): ChatItem[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map(parseChatItem).filter((item): item is ChatItem => item != null);
}
