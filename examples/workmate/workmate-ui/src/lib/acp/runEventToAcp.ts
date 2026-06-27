/** W38 Phase 1 — run_events row → ACP sessionUpdate (mirrors Java AcpOutboundConverter). */

import { ACP_META } from './acpMetaKeys';

export interface AcpSessionUpdate {
  sessionUpdate: string;
  content?: Record<string, unknown>;
  _meta?: Record<string, unknown>;
}

type RunEventEntry = {
  seq?: number;
  name?: string;
  data?: Record<string, unknown>;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function stringValue(value: unknown): string | undefined {
  return value == null ? undefined : String(value);
}

function metaWithOffset(seq: number, data: Record<string, unknown>): Record<string, unknown> {
  const meta: Record<string, unknown> = {};
  if (seq >= 0) {
    meta[ACP_META.offset] = seq;
  }
  meta[ACP_META.mode] = 'history';
  const topic = stringValue(data.topic);
  if (topic) {
    meta[ACP_META.topic] = topic;
  }
  return meta;
}

function withMemberMeta(
  baseMeta: Record<string, unknown>,
  data: Record<string, unknown>,
): Record<string, unknown> {
  const memberId = stringValue(data.memberId);
  const parentRunId = stringValue(data.parentRunId);
  if (!memberId || !parentRunId) {
    return baseMeta;
  }
  const meta = { ...baseMeta };
  const memberName = stringValue(data.memberName);
  meta[ACP_META.memberEvent] = memberName ?? memberId;
  meta[ACP_META.memberId] = memberId;
  meta[ACP_META.parentRunId] = parentRunId;
  meta.surface = 'team';
  return meta;
}

function toolCallContent(data: Record<string, unknown>, waiting = false): Record<string, unknown> {
  const content: Record<string, unknown> = {
    toolName: stringValue(data.toolName),
    toolCallId: stringValue(data.toolCallId),
  };
  if (data.args !== undefined) {
    content.args = data.args;
  }
  if (waiting) {
    content.status = 'waiting';
  }
  return content;
}

function toolCallUpdateContent(data: Record<string, unknown>): Record<string, unknown> {
  return {
    toolName: stringValue(data.toolName),
    toolCallId: stringValue(data.toolCallId),
    result: data.result,
    status: 'completed',
  };
}

function approvalWaitingContent(data: Record<string, unknown>): Record<string, unknown> {
  return {
    ...toolCallContent(data, true),
    approvalId: stringValue(data.approvalId),
    risk: stringValue(data.risk),
    summary: stringValue(data.summary),
  };
}

function questionWaitingContent(data: Record<string, unknown>): Record<string, unknown> {
  return {
    ...toolCallContent(data, true),
    questionId: stringValue(data.questionId),
    question: stringValue(data.question),
    options: data.options,
    allowFreeText: data.allowFreeText,
    multiSelect: data.multiSelect,
  };
}

function artifactContent(data: Record<string, unknown>): Record<string, unknown> {
  const content: Record<string, unknown> = {
    path: stringValue(data.path),
    name: stringValue(data.name),
    mime: stringValue(data.mime),
  };
  if (data.openInPanel === true) {
    content.openInPanel = true;
    content.preferredTab = stringValue(data.preferredTab);
  }
  return content;
}

export function runEventToAcp(entry: RunEventEntry): AcpSessionUpdate | null {
  const name = stringValue(entry.name);
  if (!name) {
    return null;
  }
  const data = asRecord(entry.data);
  const seq = typeof entry.seq === 'number' ? entry.seq : -1;
  const baseMeta = metaWithOffset(seq, data);

  switch (name) {
    case 'message.delta':
      return {
        sessionUpdate: 'agent_message_chunk',
        content: { text: stringValue(data.text) },
        _meta: withMemberMeta(baseMeta, data),
      };
    case 'tool.start':
      return {
        sessionUpdate: 'tool_call',
        content: toolCallContent(data),
        _meta: withMemberMeta(baseMeta, data),
      };
    case 'tool.end':
      return {
        sessionUpdate: 'tool_call_update',
        content: toolCallUpdateContent(data),
        _meta: withMemberMeta(baseMeta, data),
      };
    case 'approval.required':
      return {
        sessionUpdate: 'tool_call_update',
        content: approvalWaitingContent(data),
        _meta: baseMeta,
      };
    case 'question.required':
      return {
        sessionUpdate: 'tool_call_update',
        content: questionWaitingContent(data),
        _meta: baseMeta,
      };
    case 'plan.create':
    case 'plan.update':
      return {
        sessionUpdate: 'session_info_update',
        content: { plan: data },
        _meta: baseMeta,
      };
    case 'artifact.added':
      return {
        sessionUpdate: 'open_result_view',
        content: artifactContent(data),
        _meta: baseMeta,
      };
    case 'team.started':
      return {
        sessionUpdate: 'session_info_update',
        content: { teamUpdate: { type: 'team_created', payload: data } },
        _meta: baseMeta,
      };
    case 'team.member.started':
    case 'team.member.completed':
    case 'team.member.failed':
      return {
        sessionUpdate: 'session_info_update',
        content: { teamUpdate: { type: 'member_status_change', payload: data } },
        _meta: withMemberMeta(baseMeta, data),
      };
    case 'team.completed':
    case 'run.completed':
      return {
        sessionUpdate: 'session_info_update',
        content: { status: 'completed' },
        _meta: { ...baseMeta, [ACP_META.status]: 'completed' },
      };
    case 'run.failed':
    case 'run.error':
      return {
        sessionUpdate: 'session_info_update',
        content: { status: 'failed', message: stringValue(data.message) },
        _meta: { ...baseMeta, [ACP_META.status]: 'failed' },
      };
    case 'reasoning.delta':
      return {
        sessionUpdate: 'reasoning',
        content: { text: stringValue(data.text) },
        _meta: withMemberMeta(baseMeta, data),
      };
    default:
      if (name.startsWith('team.')) {
        return {
          sessionUpdate: 'session_info_update',
          content: { teamEvent: name, payload: data },
          _meta: withMemberMeta(baseMeta, data),
        };
      }
      return null;
  }
}

export function runEventLogToAcp(eventLog: RunEventEntry[]): AcpSessionUpdate[] {
  return eventLog
    .map((entry) => runEventToAcp(entry))
    .filter((update): update is AcpSessionUpdate => update != null);
}
