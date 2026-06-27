import { describe, expect, it } from 'vitest';
import { ACP_META } from './acpMetaKeys';
import { runEventToAcp } from './runEventToAcp';
import { acpToRunEvent } from './acpToRunEvent';
import { accumulateAcpStream } from './acpMessageAccumulator';

describe('acpToRunEvent', () => {
  it('maps agent_message_chunk to message.delta', () => {
    const draft = acpToRunEvent({
      sessionUpdate: 'agent_message_chunk',
      content: { text: 'hello' },
    });
    expect(draft?.name).toBe('message.delta');
    expect(draft?.data.text).toBe('hello');
  });

  it('maps member tool_call with team surface', () => {
    const draft = acpToRunEvent({
      sessionUpdate: 'tool_call',
      content: { toolName: 'workmate_read', toolCallId: 'call-1' },
      _meta: {
        [ACP_META.memberEvent]: 'PRD 写手',
        [ACP_META.memberId]: 'prd-writer',
        [ACP_META.parentRunId]: 'parent-run',
        surface: 'team',
      },
    });
    expect(draft?.name).toBe('tool.start');
    expect(draft?.data.surface).toBe('team');
    expect(draft?.data.memberId).toBe('prd-writer');
  });

  it('round-trips outbound message.delta', () => {
    const acp = runEventToAcp({ seq: 2, name: 'message.delta', data: { text: 'chunk' } });
    const draft = acpToRunEvent(acp!);
    expect(draft?.name).toBe('message.delta');
    expect(draft?.data.text).toBe('chunk');
  });
});

describe('acpMessageAccumulator', () => {
  it('merges consecutive chunks', () => {
    const drafts = accumulateAcpStream([
      { sessionUpdate: 'agent_message_chunk', content: { text: 'hel' } },
      { sessionUpdate: 'agent_message_chunk', content: { text: 'lo' } },
    ]);
    expect(drafts).toHaveLength(1);
    expect(drafts[0]?.name).toBe('message.delta');
    expect(drafts[0]?.data.text).toBe('hello');
  });

  it('flushes chunk before tool_call', () => {
    const drafts = accumulateAcpStream([
      { sessionUpdate: 'agent_message_chunk', content: { text: 'hi' } },
      { sessionUpdate: 'tool_call', content: { toolName: 'bash', toolCallId: 'c1' } },
    ]);
    expect(drafts).toHaveLength(2);
    expect(drafts[0]?.name).toBe('message.delta');
    expect(drafts[1]?.name).toBe('tool.start');
  });
});
