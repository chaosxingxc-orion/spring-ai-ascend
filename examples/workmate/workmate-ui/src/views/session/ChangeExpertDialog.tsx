import { useState } from 'react';
import type { Expert } from '../../types/api';
import { resolveExpertDisplayName } from '../../lib/teamUiLabels';

interface ChangeExpertDialogProps {
  open: boolean;
  experts: Expert[];
  currentExpertId?: string | null;
  sessionTitle: string;
  workspaceLabel?: string;
  busy?: boolean;
  onClose: () => void;
  onConfirm: (expertId: string, archiveCurrent: boolean) => void;
}

/** W47-C3 — switch expert by creating a new session with the same workspace. */
export function ChangeExpertDialog({
  open,
  experts,
  currentExpertId,
  sessionTitle,
  workspaceLabel,
  busy = false,
  onConfirm,
  onClose,
}: ChangeExpertDialogProps) {
  const [expertId, setExpertId] = useState('');
  const [archiveCurrent, setArchiveCurrent] = useState(true);

  if (!open) {
    return null;
  }

  const selectable = experts.filter((expert) => expert.id !== currentExpertId);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-labelledby="change-expert-title"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-header">
          <h3 id="change-expert-title">更换专家</h3>
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>
            ×
          </button>
        </header>
        <div className="modal-body">
          <p className="approval-summary">
            将基于当前任务「{sessionTitle}」的工作区新建会话，并切换到所选专家。当前会话不会自动删除。
          </p>
          {workspaceLabel && (
            <p className="muted">工作区：{workspaceLabel}</p>
          )}
          <label className="connector-connect-field">
            <span>新专家</span>
            <select
              className="connector-connect-input"
              value={expertId}
              onChange={(event) => setExpertId(event.target.value)}
            >
              <option value="">选择专家</option>
              {selectable.map((expert) => (
                <option key={expert.id} value={expert.id}>
                  {resolveExpertDisplayName(expert)}
                </option>
              ))}
            </select>
          </label>
          <label className="memory-settings-toggle">
            <input
              type="checkbox"
              checked={archiveCurrent}
              onChange={(event) => setArchiveCurrent(event.target.checked)}
            />
            <span>归档当前任务（推荐，避免侧栏堆积）</span>
          </label>
        </div>
        <footer className="modal-footer">
          <div className="modal-footer-actions">
            <button type="button" className="btn ghost" disabled={busy} onClick={onClose}>
              取消
            </button>
            <button
              type="button"
              className="btn primary"
              disabled={busy || !expertId}
              onClick={() => onConfirm(expertId, archiveCurrent)}
            >
              {busy ? '创建中…' : '新建并切换'}
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
