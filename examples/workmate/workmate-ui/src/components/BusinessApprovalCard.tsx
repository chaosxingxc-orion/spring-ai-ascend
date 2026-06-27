import type { ApprovalDecision, ApprovalDecisionScope } from '../types/api';
import type { TeamUiLabels } from '../lib/teamUiLabels';
import { businessApprovalFields, businessApprovalOperationLabel } from '../lib/businessApproval';

interface BusinessApprovalCardProps {
  tool: string;
  summary: string;
  reason: string;
  risk: string;
  args: Record<string, unknown>;
  status: 'pending' | 'approved' | 'denied';
  busy?: boolean;
  labels: TeamUiLabels;
  onDecide?: (decision: ApprovalDecision, scope?: ApprovalDecisionScope) => void;
}

export function BusinessApprovalCard({
  tool,
  summary,
  reason,
  risk,
  args,
  status,
  busy = false,
  labels,
  onDecide,
}: BusinessApprovalCardProps) {
  const fields = businessApprovalFields(tool, args);
  const operation = businessApprovalOperationLabel(tool, summary);
  const resolved = status !== 'pending';

  return (
    <article
      className={`business-approval-card status-${status}`}
      aria-label={labels.approvalTitle}
    >
      <header className="business-approval-head">
        <span className="business-approval-icon" aria-hidden>📋</span>
        <div className="business-approval-head-text">
          <h4 className="business-approval-title">{labels.approvalTitle}</h4>
          <p className="business-approval-operation">{operation}</p>
        </div>
        {resolved && (
          <span className={`business-approval-status status-${status}`}>
            {status === 'approved' ? labels.approvalApprove : labels.approvalDeny}
          </span>
        )}
      </header>
      <dl className="business-approval-fields">
        {fields.map((field) => (
          <div key={`${field.label}-${field.value}`}>
            <dt>{field.label}</dt>
            <dd>{field.value}</dd>
          </div>
        ))}
      </dl>
      {reason && <p className="business-approval-reason muted">{reason}</p>}
      {risk && <p className="business-approval-risk">风险等级：{risk}</p>}
      {!resolved && onDecide && (
        <footer className="business-approval-actions">
          <button
            type="button"
            className="btn danger"
            disabled={busy}
            onClick={() => onDecide('deny')}
          >
            {labels.approvalDeny}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={busy}
            onClick={() => onDecide('approve', 'ONCE')}
          >
            {labels.approvalApprove}
          </button>
        </footer>
      )}
    </article>
  );
}
