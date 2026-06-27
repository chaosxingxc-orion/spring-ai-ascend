import { describe, expect, it } from 'vitest';
import { buildApprovalDecisionBody } from './approvalScope';

describe('buildApprovalDecisionBody', () => {
  it('sends explicit scope for trust granularity', () => {
    expect(buildApprovalDecisionBody('approve', 'ONCE')).toEqual({ decision: 'approve', scope: 'ONCE' });
    expect(buildApprovalDecisionBody('approve', 'SESSION')).toEqual({ decision: 'approve', scope: 'SESSION' });
    expect(buildApprovalDecisionBody('deny')).toEqual({ decision: 'deny' });
  });

  it('maps legacy always=true to SESSION via always field', () => {
    expect(buildApprovalDecisionBody('approve', undefined, true)).toEqual({ decision: 'approve', always: true });
  });
});
