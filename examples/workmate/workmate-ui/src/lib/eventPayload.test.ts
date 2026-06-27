import { describe, expect, it } from 'vitest';
import {
  parseChatItems,
  parseMessageDelta,
  parseTeamMemberEvent,
  parseToolEnd,
  parseToolStart,
  isTeamSurfacePayload,
} from './eventPayload';

describe('parseMessageDelta', () => {
  it('reads plain string text', () => {
    expect(parseMessageDelta({ text: '调研结论' }).text).toBe('调研结论');
  });

  it('reads leader delta key', () => {
    expect(parseMessageDelta({ delta: 'leader text' }).text).toBe('leader text');
  });

  it('unwraps audit-redacted preview object (member sub-run hydrate)', () => {
    expect(parseMessageDelta({ text: { preview: '我来分析', bytes: 12 } }).text).toBe('我来分析');
  });
});

describe('parseToolStart', () => {
  it('reads toolCallId when present', () => {
    const payload = parseToolStart({
      toolName: 'workmate_bash__abc',
      toolCallId: 'call-1',
      args: { command: 'ls' },
    });
    expect(payload.toolCallId).toBe('call-1');
    expect(payload.toolName).toBe('workmate_bash__abc');
  });
});

describe('parseToolEnd', () => {
  it('reads toolCallId when present', () => {
    const payload = parseToolEnd({
      toolName: 'workmate_bash__abc',
      toolCallId: 'call-1',
      result: { success: true },
    });
    expect(payload.toolCallId).toBe('call-1');
    expect(payload.status).toBe('success');
  });

  it('marks failed results', () => {
    const payload = parseToolEnd({
      toolName: 'workmate_bash__abc',
      result: { success: false, message: 'denied' },
    });
    expect(payload.status).toBe('failed');
  });
});

describe('parseTeamMemberEvent', () => {
  it('reads memberId and names', () => {
    const payload = parseTeamMemberEvent({
      memberId: 'prd-writer',
      memberName: 'PRD 写手',
      subRunId: 'run:prd-writer',
      parentRunId: 'run',
    });
    expect(payload?.memberId).toBe('prd-writer');
    expect(payload?.memberName).toBe('PRD 写手');
  });
});

describe('parseChatItems', () => {
  it('filters invalid chat rows', () => {
    const items = parseChatItems([
      { id: 'u1', kind: 'user', text: 'hi' },
      { kind: 'broken' },
      { id: 't1', kind: 'tool', toolName: 'bash', status: 'running', toolCallId: 'c1' },
    ]);
    expect(items).toHaveLength(2);
    expect(items[1].kind).toBe('tool');
    if (items[1].kind === 'tool') {
      expect(items[1].toolCallId).toBe('c1');
      expect(items[1].status).toBe('executing');
    }
  });

  it('normalizes legacy done/error statuses from server', () => {
    const items = parseChatItems([
      { id: 't1', kind: 'tool', toolName: 'bash', status: 'done' },
      { id: 't2', kind: 'tool', toolName: 'bash', status: 'error' },
    ]);
    if (items[0].kind === 'tool') {
      expect(items[0].status).toBe('success');
    }
    if (items[1].kind === 'tool') {
      expect(items[1].status).toBe('failed');
    }
  });

  it('parses JSON string tool args from session_messages', () => {
    const items = parseChatItems([
      {
        id: 't-bus',
        kind: 'tool',
        toolName: 'workmate_team_bus_publish__session',
        status: 'done',
        args: '{"topic":"content-writer","body":"hello"}',
        result: '{"success":true,"data":{"topic":"content-writer"}}',
      },
    ]);
    expect(items).toHaveLength(1);
    if (items[0].kind === 'tool') {
      expect(items[0].args).toEqual({ topic: 'content-writer', body: 'hello' });
      expect(items[0].result).toEqual({ success: true, data: { topic: 'content-writer' } });
    }
  });

  it('preserves seq on user and assistant rows', () => {
    const items = parseChatItems([
      { id: 'u1', kind: 'user', text: 'hi', seq: 1 },
      { id: 'a1', kind: 'assistant', text: 'hello', seq: 2 },
    ]);
    expect(items[0].kind === 'user' && items[0].seq).toBe(1);
    expect(items[1].kind === 'assistant' && items[1].seq).toBe(2);
  });

  it('parses user image attachments', () => {
    const items = parseChatItems([
      {
        id: 'u1',
        kind: 'user',
        text: 'see screenshot',
        attachments: [{ path: 'shots/ui.png', mime: 'image/png' }],
      },
    ]);
    expect(items).toHaveLength(1);
    if (items[0]?.kind === 'user') {
      expect(items[0].attachments?.[0]?.path).toBe('shots/ui.png');
    }
  });

  it('filters team-surface tool rows from main chat', () => {
    const items = parseChatItems([
      { id: 'u1', kind: 'user', text: 'hi' },
      { id: 't1', kind: 'tool', toolName: 'bash', status: 'done', surface: 'team' },
      { id: 't2', kind: 'tool', toolName: 'bash', status: 'done' },
    ]);
    expect(items).toHaveLength(2);
    expect(items.some((item) => item.kind === 'tool')).toBe(true);
  });
});

describe('isTeamSurfacePayload', () => {
  it('detects member sub-run SSE payloads', () => {
    expect(isTeamSurfacePayload({ memberId: 'writer', parentRunId: 'run-1' })).toBe(true);
    expect(isTeamSurfacePayload({ surface: 'team' })).toBe(true);
    expect(isTeamSurfacePayload({ toolName: 'bash' })).toBe(false);
  });
});
