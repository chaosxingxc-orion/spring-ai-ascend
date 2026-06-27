import type { ApprovalRequiredPayload } from '../types/events';
import type { ApprovalDecision, ApprovalDecisionScope } from '../types/api';
import type { TeamUiLabels } from '../lib/teamUiLabels';
import { businessApprovalFields, businessApprovalOperationLabel, isBusinessApprovalTool } from '../lib/businessApproval';
import { formatToolName } from '../lib/toolDisplay';

interface ApprovalModalProps {
  pending: ApprovalRequiredPayload | null;
  busy: boolean;
  labels: TeamUiLabels;
  onDecide: (decision: ApprovalDecision, scope?: ApprovalDecisionScope) => void;
  onClose: () => void;
}

export function ApprovalModal({ pending, busy, labels, onDecide, onClose }: ApprovalModalProps) {
  if (!pending) {
    return null;
  }

  const business = isBusinessApprovalTool(pending.tool);
  const title = business ? labels.approvalTitle : '需要审批';
  const summary = business
    ? businessApprovalOperationLabel(pending.tool, pending.summary)
    : pending.summary || formatToolName(pending.tool);
  const businessFields = business ? businessApprovalFields(pending.tool, pending.args) : [];

  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal" role="dialog" aria-labelledby="approval-title">
        <header className="modal-header">
          <h3 id="approval-title">{title}</h3>
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="approval-summary">{summary}</p>
          {business && businessFields.length > 0 && (
            <dl className="approval-meta business-approval-fields">
              {businessFields.map((field) => (
                <div key={`${field.label}-${field.value}`}>
                  <dt>{field.label}</dt>
                  <dd>{field.value}</dd>
                </div>
              ))}
            </dl>
          )}
          {!business && (
            <dl className="approval-meta">
              <div>
                <dt>工具</dt>
                <dd>{formatToolName(pending.tool)}</dd>
              </div>
              <div>
                <dt>风险</dt>
                <dd className={`risk-${pending.risk}`}>{pending.risk}</dd>
              </div>
              <div>
                <dt>原因</dt>
                <dd>{pending.reason}</dd>
              </div>
            </dl>
          )}
          {!business && (
            <details>
              <summary>参数详情</summary>
              <pre>{JSON.stringify(pending.args, null, 2)}</pre>
            </details>
          )}
        </div>
        <footer className="modal-footer">
          <p className="approval-hint">
            沙箱模式下，敏感工具需你确认后才会执行。「仅此次」只放行当前操作；「本会话均允许」对同类工具在本任务内不再询问。
            关闭弹窗不会解除阻塞；若服务已重启导致审批过期，请点「拒绝」或关闭后重试任务。
          </p>
          <div className="modal-footer-actions">
          <button
            type="button"
            className="btn danger"
            disabled={busy}
            onClick={() => onDecide('deny')}
          >
            {business ? labels.approvalDeny : '拒绝'}
          </button>
          {!business && (
            <>
              <button
                type="button"
                className="btn secondary"
                disabled={busy}
                onClick={() => onDecide('approve', 'SESSION')}
              >
                本会话均允许
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={busy}
                onClick={() => onDecide('approve', 'ONCE')}
              >
                仅此次允许
              </button>
            </>
          )}
          {business && (
            <button
              type="button"
              className="btn primary"
              disabled={busy}
              onClick={() => onDecide('approve', 'ONCE')}
            >
              {labels.approvalApprove}
            </button>
          )}
          </div>
        </footer>
      </div>
    </div>
  );
}
