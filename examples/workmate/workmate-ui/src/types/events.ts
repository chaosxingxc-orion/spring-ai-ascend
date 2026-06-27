import type { MentionRef } from './mention';

export type ToolStatus = 'executing' | 'success' | 'failed' | 'waiting';

export type PlanStepStatus = 'pending' | 'active' | 'done';

export interface PlanStep {
  id: string;
  title: string;
  status?: PlanStepStatus;
}

export interface PlanPayload {
  planId: string;
  title?: string;
  steps: PlanStep[];
}

export interface UserAttachment {
  path: string;
  name?: string;
  mime?: string;
}

export type ToolChatItem = {
  id: string;
  kind: 'tool';
  toolName: string;
  toolCallId?: string;
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  /** Canonical run-event sequence; primary ordering key (stable across refresh). */
  seq?: number;
  /** Wall-clock ms used only for display; never the cross-refresh ordering key. */
  startedAt?: number;
  endedAt?: number;
  memberId?: string;
  memberName?: string;
};

/**
 * Cross-cutting optional fields that ordering/hydration code reads generically across all chat-item
 * kinds (the canonical run-event `seq`, and team member attribution). Declared on a shared base and
 * intersected with every variant so accessors don't need per-kind narrowing.
 */
type ChatItemCommon = {
  seq?: number;
  memberId?: string;
  memberName?: string;
  toolCallId?: string;
};

type ChatItemVariant =
  | { id: string; kind: 'user'; text: string; seq?: number; mentions?: MentionRef[]; attachments?: UserAttachment[] }
  | { id: string; kind: 'assistant'; text: string; seq?: number; memberId?: string; memberName?: string }
  | { id: string; kind: 'reasoning'; text: string; seq?: number; memberId?: string; memberName?: string }
  | ToolChatItem
  | { id: string; kind: 'system'; text: string; tone?: 'error' | 'info'; seq?: number }
  | { id: string; kind: 'tool-group'; tools: ToolChatItem[] }
  | {
      id: string;
      kind: 'plan';
      planId: string;
      title?: string;
      steps: PlanStep[];
      confirmed?: boolean;
    }
  | {
      id: string;
      kind: 'approval';
      approvalId: string;
      tool: string;
      risk: string;
      reason: string;
      summary: string;
      args: Record<string, unknown>;
      status: 'pending' | 'approved' | 'denied';
      seq?: number;
    }
  | {
      id: string;
      kind: 'question';
      questionId: string;
      question: string;
      options: string[];
      allowFreeText: boolean;
      multiSelect: boolean;
      status: 'pending' | 'answered' | 'skipped' | 'cancelled';
      selections?: string[];
      answerText?: string;
      seq?: number;
    }
  | {
      id: string;
      kind: 'artifact-cta';
      path: string;
      name: string;
      mime: string;
      preferredTab?: 'browser' | 'source' | 'changes';
    }
  | {
      id: string;
      kind: 'member-delegation';
      message?: string;
      description?: string;
      truncated?: boolean;
      /** 1-based dispatch round for this member in the session. */
      round: number;
      reawaken?: boolean;
      seq?: number;
    }
  | {
      id: string;
      kind: 'expert-switched';
      fromExpertId?: string;
      toExpertId: string;
      fromExpertName?: string;
      toExpertName: string;
      newGeneration?: number;
      mode?: string;
      seq?: number;
    };

export type ChatItem = ChatItemVariant & ChatItemCommon;

export interface ApprovalRequiredPayload {
  approvalId: string;
  sessionId: string;
  tool: string;
  toolCallId?: string;
  risk: string;
  reason: string;
  summary: string;
  args: Record<string, unknown>;
}

export interface QuestionRequiredPayload {
  questionId: string;
  sessionId: string;
  question: string;
  options: string[];
  allowFreeText: boolean;
  multiSelect: boolean;
  toolName?: string;
  /** Stable card id from backend (hash of confirmation step); prefer over questionId UUID. */
  messageId?: string;
}

export interface ArtifactAddedPayload {
  path: string;
  name: string;
  mime: string;
  size: number;
  updatedAt: string;
  openInPanel?: boolean;
  preferredTab?: 'browser' | 'source' | 'changes';
}

export interface UsageDeltaPayload {
  deltaPromptTokens?: number;
  deltaCompletionTokens?: number;
  totalPromptTokens: number;
  totalCompletionTokens: number;
  model?: string;
  memberId?: string;
}

export type TeamCollaboration = 'sequential' | 'parallel';

export type CoordinationPattern =
  | 'orchestrator'
  | 'pipeline'
  | 'agent-team'
  | 'generator-verifier'
  | 'message-bus'
  | 'shared-state';

export interface TeamLeadPayload {
  name?: string;
  title?: string;
  avatar?: string;
}

export interface TeamRosterEntry {
  memberId: string;
  memberName?: string;
  order?: number;
  role?: string;
  avatar?: string;
  participantRole?: string;
}

export interface TeamStartedPayload {
  teamId: string;
  parentRunId?: string;
  memberCount?: number;
  collaboration?: TeamCollaboration;
  pattern?: CoordinationPattern;
  maxIterations?: number;
  convergenceTarget?: number;
  busMode?: string;
  topicBusProvider?: string;
  lead?: TeamLeadPayload;
  members?: TeamRosterEntry[];
  teamRuntime?: string;
}

export interface TeamIterationPayload {
  teamId?: string;
  parentRunId?: string;
  iteration: number;
  maxIterations: number;
}

export interface TeamVerifyPayload {
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
}

export interface TeamMemberEventPayload {
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
}

export interface TeamMemoryPayload {
  parentRunId?: string;
  path: string;
  section: string;
  preview?: string;
  totalBytes?: number;
  action?: 'init' | 'append' | string;
}

export type SseEventName =
  | 'message.delta'
  | 'tool.start'
  | 'tool.end'
  | 'approval.required'
  | 'question.required'
  | 'question.answered'
  | 'question.cancelled'
  | 'expert.switched'
  | 'run.completed'
  | 'run.failed'
  | 'run.error'
  | 'artifact.added'
  | 'plan.create'
  | 'plan.update'
  | 'usage.delta'
  | 'team.started'
  | 'team.build.completed'
  | 'team.member.started'
  | 'team.member.message'
  | 'team.member.reawakened'
  | 'team.member.completed'
  | 'team.member.paused'
  | 'team.member.failed'
  | 'team.phase.started'
  | 'team.phase.completed'
  | 'team.lead.synthesizing'
  | 'team.iteration.started'
  | 'team.verify.started'
  | 'team.verify.accepted'
  | 'team.verify.rejected'
  | 'team.memory'
  | 'team.completed'
  | 'heartbeat';

export interface SseEvent {
  name: SseEventName | string;
  data: unknown;
  id?: string;
}
