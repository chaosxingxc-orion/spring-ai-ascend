import { describe, expect, it } from 'vitest';
import {
  buildApprovalChatItem,
  businessApprovalFields,
  isBusinessApprovalTool,
  sessionHasPendingApproval,
} from './businessApproval';

describe('businessApproval', () => {
  it('detects MCP tools as business approval', () => {
    expect(isBusinessApprovalTool('mcp__oa__submit_credit_memo')).toBe(true);
    expect(isBusinessApprovalTool('bash')).toBe(false);
  });

  it('extracts business fields from args', () => {
    const fields = businessApprovalFields('mcp__oa__submit_credit_memo', {
      customerName: 'XX企业',
      creditAmount: '5000万',
      operation: '提交授信审批',
    });
    expect(fields).toEqual(
      expect.arrayContaining([
        { label: '操作', value: '提交授信审批' },
        { label: '客户', value: 'XX企业' },
        { label: '授信额度', value: '5000万' },
      ]),
    );
  });

  it('builds timeline approval item from SSE payload', () => {
    const item = buildApprovalChatItem({
      approvalId: 'a1',
      sessionId: 's1',
      tool: 'mcp__oa__submit_credit_memo',
      risk: 'HIGH',
      reason: 'Business submission',
      summary: '提交授信审批',
      args: { customerName: 'XX企业' },
    });
    expect(item).toMatchObject({
      id: 'approval-a1',
      kind: 'approval',
      status: 'pending',
    });
  });

  it('detects pending approval from chat timeline', () => {
    expect(
      sessionHasPendingApproval(
        's1',
        {
          s1: [{
            id: 'approval-a1',
            kind: 'approval',
            approvalId: 'a1',
            tool: 'mcp__oa__submit_credit_memo',
            risk: 'HIGH',
            reason: '',
            summary: '',
            args: {},
            status: 'pending',
          }],
        },
        {},
      ),
    ).toBe(true);
    expect(sessionHasPendingApproval('s1', {}, {})).toBe(false);
  });
});
