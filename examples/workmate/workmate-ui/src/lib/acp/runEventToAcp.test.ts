import { describe, expect, it } from 'vitest';
import { ACP_META } from './acpMetaKeys';
import { runEventLogToAcp, runEventToAcp } from './runEventToAcp';

describe('runEventToAcp', () => {
  it('maps message.delta to agent_message_chunk', () => {
    const acp = runEventToAcp({
      seq: 3,
      name: 'message.delta',
      data: { text: 'hello' },
    });
    expect(acp?.sessionUpdate).toBe('agent_message_chunk');
    expect(acp?.content?.text).toBe('hello');
    expect(acp?._meta?.[ACP_META.offset]).toBe(3);
  });

  it('maps member tool.start with team meta', () => {
    const acp = runEventToAcp({
      seq: 7,
      name: 'tool.start',
      data: {
        toolName: 'workmate_read',
        toolCallId: 'call-1',
        memberId: 'prd-writer',
        memberName: 'PRD 写手',
        parentRunId: 'parent-run',
      },
    });
    expect(acp?.sessionUpdate).toBe('tool_call');
    expect(acp?._meta?.[ACP_META.memberEvent]).toBe('PRD 写手');
    expect(acp?._meta?.surface).toBe('team');
  });

  it('maps artifact.added to open_result_view', () => {
    const acp = runEventToAcp({
      seq: 9,
      name: 'artifact.added',
      data: {
        path: 'outputs/index.html',
        name: 'index.html',
        mime: 'text/html',
        openInPanel: true,
        preferredTab: 'browser',
      },
    });
    expect(acp?.sessionUpdate).toBe('open_result_view');
    expect(acp?.content?.path).toBe('outputs/index.html');
    expect(acp?.content?.openInPanel).toBe(true);
  });

  it('converts event log in order', () => {
    const log = runEventLogToAcp([
      { seq: 1, name: 'tool.start', data: { toolName: 'bash', toolCallId: 'c1' } },
      {
        seq: 2,
        name: 'tool.end',
        data: { toolName: 'bash', toolCallId: 'c1', result: { success: true } },
      },
    ]);
    expect(log).toHaveLength(2);
    expect(log[0]?.sessionUpdate).toBe('tool_call');
    expect(log[1]?.sessionUpdate).toBe('tool_call_update');
  });
});
