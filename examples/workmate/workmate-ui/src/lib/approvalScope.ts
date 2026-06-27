import type { ApprovalDecision, ApprovalDecisionScope } from '../types/api';

/** Request body for POST /approvals/{id} (G25 trust granularity). */
export function buildApprovalDecisionBody(
  decision: ApprovalDecision,
  scope?: ApprovalDecisionScope,
  always?: boolean,
): { decision: ApprovalDecision; scope?: ApprovalDecisionScope; always?: boolean } {
  const body: { decision: ApprovalDecision; scope?: ApprovalDecisionScope; always?: boolean } = {
    decision,
  };
  if (scope) {
    body.scope = scope;
  } else if (always) {
    body.always = true;
  }
  return body;
}
