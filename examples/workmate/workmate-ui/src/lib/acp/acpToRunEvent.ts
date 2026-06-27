/** W38 Phase 2 — ACP sessionUpdate → run_events draft (mirrors Java AcpInboundConverter). */

import type { AcpSessionUpdate } from './runEventToAcp';
import type { RunEventDraft } from './acpTypes';
import { ACP_META } from './acpMetaKeys';

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function stringValue(value: unknown): string | undefined {
  return value == null ? undefined : String(value);
}

function applyMemberFields(
  payload: Record<string, unknown>,
  meta: Record<string, unknown>,
): Record<string, unknown> {
  if (Object.keys(meta).length === 0) {
    return payload;
  }
  const next = { ...payload };
  if (meta.surface === 'team') {
    next.surface = 'team';
  }
  const memberEvent = stringValue(meta[ACP_META.memberEvent]);
  if (memberEvent && next.memberName === undefined) {
    next.memberName = memberEvent;
  }
  const memberId = stringValue(meta[ACP_META.memberId]);
  if (memberId) {
    next.memberId = memberId;
  }
  const parentRunId = stringValue(meta[ACP_META.parentRunId]);
  if (parentRunId) {
    next.parentRunId = parentRunId;
  }
  if (next.memberId && !next.parentRunId) {
    next.parentRunId = 'member-run';
  }
  return next;
}

function toolStartPayload(
  content: Record<string, unknown>,
  memberBase: Record<string, unknown>,
): Record<string, unknown> {
  const start: Record<string, unknown> = { ...memberBase };
  start.toolName = stringValue(content.toolName);
  start.toolCallId = stringValue(content.toolCallId);
  if (content.args !== undefined) {
    start.args = content.args;
  }
  return start;
}

function convertToolCallUpdate(
  content: Record<string, unknown>,
  memberBase: Record<string, unknown>,
): RunEventDraft {
  if (content.status === 'waiting') {
    return {
      name: 'approval.required',
      data: {
        ...memberBase,
        toolName: stringValue(content.toolName),
        toolCallId: stringValue(content.toolCallId),
        approvalId: stringValue(content.approvalId),
        risk: stringValue(content.risk),
        summary: stringValue(content.summary),
        ...(content.args !== undefined ? { args: content.args } : {}),
      },
    };
  }
  return {
    name: 'tool.end',
    data: {
      ...memberBase,
      toolName: stringValue(content.toolName),
      toolCallId: stringValue(content.toolCallId),
      result: content.result,
    },
  };
}

function convertSessionInfoUpdate(
  content: Record<string, unknown>,
  memberBase: Record<string, unknown>,
): RunEventDraft | null {
  if (content.plan) {
    const plan = asRecord(content.plan);
    const name = plan.planId || plan.steps ? 'plan.update' : 'plan.create';
    return { name, data: plan };
  }
  const teamUpdate = asRecord(content.teamUpdate);
  if (Object.keys(teamUpdate).length > 0) {
    const type = stringValue(teamUpdate.type);
    const teamPayload = applyMemberFields(asRecord(teamUpdate.payload), memberBase);
    if (type === 'team_created') {
      return { name: 'team.started', data: teamPayload };
    }
    if (type === 'member_status_change') {
      return { name: 'team.member.completed', data: teamPayload };
    }
  }
  if (content.status === 'completed') {
    return { name: 'run.completed', data: { ...memberBase } };
  }
  if (content.status === 'failed') {
    return {
      name: 'run.failed',
      data: { ...memberBase, message: stringValue(content.message) },
    };
  }
  if (content.teamEvent) {
    return {
      name: String(content.teamEvent),
      data: asRecord(content.payload),
    };
  }
  return null;
}

export function acpToRunEvent(update: AcpSessionUpdate | Record<string, unknown>): RunEventDraft | null {
  const sessionUpdate = stringValue(
    (update as AcpSessionUpdate).sessionUpdate ?? (update as Record<string, unknown>).sessionUpdate,
  );
  if (!sessionUpdate) {
    return null;
  }
  const content = asRecord((update as AcpSessionUpdate).content ?? (update as Record<string, unknown>).content);
  const meta = asRecord((update as AcpSessionUpdate)._meta ?? (update as Record<string, unknown>)._meta);
  const memberBase = applyMemberFields({}, meta);

  switch (sessionUpdate) {
    case 'agent_message_chunk':
      return {
        name: 'message.delta',
        data: applyMemberFields({ text: stringValue(content.text) }, meta),
      };
    case 'reasoning':
    case 'reasoning_text':
      return {
        name: 'reasoning.delta',
        data: applyMemberFields({ text: stringValue(content.text) }, meta),
      };
    case 'tool_call':
      return { name: 'tool.start', data: toolStartPayload(content, memberBase) };
    case 'tool_call_update':
      return convertToolCallUpdate(content, memberBase);
    case 'open_result_view':
      return {
        name: 'artifact.added',
        data: {
          path: stringValue(content.path),
          name: stringValue(content.name),
          mime: stringValue(content.mime),
          ...(content.openInPanel === true
            ? { openInPanel: true, preferredTab: stringValue(content.preferredTab) }
            : {}),
        },
      };
    case 'user':
      return {
        name: 'message.user',
        data: applyMemberFields({ text: stringValue(content.text) }, meta),
      };
    case 'session_info_update':
      return convertSessionInfoUpdate(content, memberBase);
    default:
      return null;
  }
}
