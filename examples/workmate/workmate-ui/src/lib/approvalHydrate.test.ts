import { describe, expect, it } from 'vitest';
import { modalPendingApprovalFromRunEvents } from './approvalHydrate';
import type { ChatItem } from '../types/events';

describe('approvalHydrate', () => {
  it('returns latest unresolved modal approval from run_events', () => {
    const payload = modalPendingApprovalFromRunEvents(
      [
        {
          seq: 1,
          name: 'approval.required',
          data: {
            approvalId: 'a1',
            sessionId: 's1',
            tool: 'workmate_bash',
            risk: 'high',
            reason: 'rm',
            summary: 'delete',
            args: { cmd: 'rm -rf tmp' },
          },
        },
      ],
      [],
    );
    expect(payload?.approvalId).toBe('a1');
    expect(payload?.tool).toBe('workmate_bash');
  });

  it('skips resolved approvals', () => {
    const chat: ChatItem[] = [
      {
        id: 'approval-a1',
        kind: 'approval',
        approvalId: 'a1',
        tool: 'qieman-mcp__trade',
        risk: 'medium',
        reason: '',
        summary: '',
        args: {},
        status: 'approved',
      },
    ];
    const payload = modalPendingApprovalFromRunEvents(
      [
        {
          seq: 2,
          name: 'approval.required',
          data: {
            approvalId: 'a1',
            tool: 'qieman-mcp__trade',
            risk: 'medium',
          },
        },
      ],
      chat,
    );
    expect(payload).toBeNull();
  });
});
