import { useEffect, useState } from 'react';
import type { DelegationInput } from '../lib/delegationInput';

interface MemberTaskMessageProps {
  delegation: DelegationInput;
  memberName?: string;
  /** When set, labels the card as a distinct re-dispatch round. */
  round?: number;
}

/**
 * The task the leader delegated to a member. Long prompts open in a modal so the full markdown
 * body is never clipped with ellipsis inside the timeline card.
 */
export function MemberTaskMessage({ delegation, memberName, round }: MemberTaskMessageProps) {
  const [dialogOpen, setDialogOpen] = useState(false);
  const body = delegation.message ?? delegation.description ?? '';
  const title = delegation.description && delegation.message && delegation.description !== delegation.message
    ? delegation.description
    : undefined;
  const previewLine = title ?? body.split('\n').find((line) => line.trim()) ?? '';
  const needsDialog = body.length > 0 && (delegation.truncated || body.length > 180 || body.split('\n').length > 6);
  const dialogBody = body;

  useEffect(() => {
    if (!dialogOpen) {
      return undefined;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setDialogOpen(false);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [dialogOpen]);

  if (!body) {
    return null;
  }

  return (
    <>
      <div className="agent-member-task-message">
        <span className="agent-member-task-label">
          {round != null && round > 1 ? `第 ${round} 次派活` : '派给该成员的任务'}
        </span>
        {title && <p className="agent-member-task-title">{title}</p>}
        {!needsDialog && (
          <pre className="agent-member-task-body expanded">{body}</pre>
        )}
        {needsDialog && (
          <>
            <p className="agent-member-task-preview">{previewLine}</p>
            <button
              type="button"
              className="agent-member-task-toggle"
              onClick={() => setDialogOpen(true)}
            >
              点击查看完整任务输入
            </button>
          </>
        )}
      </div>
      {dialogOpen && (
        <div
          className="modal-backdrop agent-member-task-dialog-backdrop"
          role="presentation"
          onClick={() => setDialogOpen(false)}
        >
          <div
            className="modal agent-member-task-dialog"
            role="dialog"
            aria-modal="true"
            aria-label="完整任务输入"
            onClick={(event) => event.stopPropagation()}
          >
            <header className="modal-header">
              <h3>{memberName ? `派给 ${memberName} 的任务` : '完整任务输入'}</h3>
              <button type="button" className="btn ghost sm" onClick={() => setDialogOpen(false)}>
                关闭
              </button>
            </header>
            <div className="modal-body agent-member-task-dialog-body">
              <pre className="agent-member-task-body expanded">{dialogBody}</pre>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
