import { describe, expect, it } from 'vitest';
import { accumulateAcpStream } from './acp/acpMessageAccumulator';
import { ACP_META } from './acp/acpMetaKeys';
import { runEventToAcp } from './acp/runEventToAcp';
import {
  draftsToRunEventRows,
  filterMemberRunEvents,
  isMemberRunEvent,
  isLeaderRunTerminal,
  isTeamSurfacePayload,
  projectMemberTrace,
} from './memberEventProjection';

const MEMBER_TOOL_FIXTURE = {
  sessionUpdate: 'tool_call',
  content: { toolName: 'workmate_read', toolCallId: 'call-1', args: { path: 'notes.md' } },
  _meta: {
    [ACP_META.memberEvent]: 'software-engineer',
    [ACP_META.memberId]: 'software-engineer',
    [ACP_META.parentRunId]: 'parent-run',
    surface: 'team',
  },
};

describe('memberEventProjection', () => {
  it('detects team surface by explicit surface or member sub-run ids', () => {
    expect(isTeamSurfacePayload({ surface: 'team' })).toBe(true);
    expect(isTeamSurfacePayload({ memberId: 'writer', parentRunId: 'run-1' })).toBe(true);
    expect(isTeamSurfacePayload({ toolName: 'bash' })).toBe(false);
  });

  it('treats member run.completed as non-leader terminal', () => {
    expect(isLeaderRunTerminal('run.completed', { memberId: 'writer', parentRunId: 'p1' })).toBe(false);
    expect(isLeaderRunTerminal('run.completed', {})).toBe(true);
    expect(isLeaderRunTerminal('team.completed', {})).toBe(true);
  });

  it('filters member run events by memberId', () => {
    const events = [
      { seq: 1, name: 'tool.start', data: { surface: 'team', memberId: 'writer', parentRunId: 'p' } },
      { seq: 2, name: 'tool.start', data: { surface: 'team', memberId: 'reviewer', parentRunId: 'p' } },
    ];
    expect(filterMemberRunEvents(events, 'writer')).toHaveLength(1);
    expect(isMemberRunEvent(events[0]?.data, 'writer')).toBe(true);
    expect(isMemberRunEvent(events[1]?.data, 'writer')).toBe(false);
  });

  it('projects member trace with merged assistant text and paired tools', () => {
    const events = [
      {
        seq: 10,
        name: 'message.delta',
        data: { surface: 'team', memberId: 'writer', parentRunId: 'p', text: 'hel' },
      },
      {
        seq: 11,
        name: 'message.delta',
        data: { surface: 'team', memberId: 'writer', parentRunId: 'p', text: 'lo' },
      },
      {
        seq: 12,
        name: 'tool.start',
        data: {
          surface: 'team',
          memberId: 'writer',
          parentRunId: 'p',
          toolName: 'workmate_read',
          toolCallId: 'c1',
        },
      },
      {
        seq: 13,
        name: 'tool.end',
        data: {
          surface: 'team',
          memberId: 'writer',
          parentRunId: 'p',
          toolName: 'workmate_read',
          toolCallId: 'c1',
          result: { success: true },
        },
      },
    ];

    const trace = projectMemberTrace(events, 'writer');
    expect(trace).toHaveLength(2);
    expect(trace[0]?.kind).toBe('assistant');
    expect(trace[0]?.text).toBe('hello');
    expect(trace[1]?.kind).toBe('tool');
    if (trace[1]?.kind === 'tool') {
      expect(trace[1].status).toBe('success');
    }
  });

  it('round-trips ACP member tool_call through accumulator into member trace', () => {
    const drafts = accumulateAcpStream([
      MEMBER_TOOL_FIXTURE,
      {
        sessionUpdate: 'tool_call_update',
        content: {
          toolName: 'workmate_read',
          toolCallId: 'call-1',
          result: { success: true, data: { path: 'notes.md' } },
        },
        _meta: MEMBER_TOOL_FIXTURE._meta,
      },
    ]);
    const rows = draftsToRunEventRows(drafts);
    const trace = projectMemberTrace(rows, 'software-engineer');
    expect(trace).toHaveLength(1);
    expect(trace[0]?.kind).toBe('tool');
    if (trace[0]?.kind === 'tool') {
      expect(trace[0].toolName).toBe('workmate_read');
      expect(trace[0].status).toBe('success');
    }
  });

  it('round-trips outbound run_events member tool to ACP and back', () => {
    const outbound = runEventToAcp({
      seq: 4,
      name: 'tool.start',
      data: {
        surface: 'team',
        memberId: 'writer',
        memberName: '内容写手',
        parentRunId: 'parent-run',
        toolName: 'workmate_bash',
        toolCallId: 'call-9',
      },
    });
    expect(outbound?._meta?.surface).toBe('team');
    const drafts = accumulateAcpStream(outbound ? [outbound] : []);
    const trace = projectMemberTrace(draftsToRunEventRows(drafts), 'writer');
    expect(trace[0]?.kind).toBe('tool');
  });
});
