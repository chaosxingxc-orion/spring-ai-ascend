import { useMemo, useState } from 'react';
import type { PlanStep } from '../../types/events';
import { diffPlanSteps, diffPlanTitle, type PlanStepChange } from '../../lib/planDiff';

interface PlanCardProps {
  planId: string;
  title?: string;
  steps: PlanStep[];
  confirmed?: boolean;
  confirming?: boolean;
  saving?: boolean;
  onConfirm?: () => void;
  onSaveSteps?: (steps: PlanStep[], title?: string) => void | Promise<void>;
}

/** W15d + W37-B5 — Plan 卡 + 确认前可编辑 steps */
export function PlanCard({
  planId,
  title,
  steps,
  confirmed = false,
  confirming = false,
  saving = false,
  onConfirm,
  onSaveSteps,
}: PlanCardProps) {
  const [editing, setEditing] = useState(false);
  const [draftTitle, setDraftTitle] = useState(title ?? '');
  const [draftSteps, setDraftSteps] = useState<PlanStep[]>(steps);

  const canEdit = !confirmed && Boolean(onSaveSteps);

  const stepRows = useMemo(
    () => (editing ? draftSteps : steps),
    [draftSteps, editing, steps],
  );

  const editDiff = useMemo((): PlanStepChange[] => {
    if (!editing) {
      return [];
    }
    const titleChange = diffPlanTitle(title, draftTitle);
    const stepChanges = diffPlanSteps(steps, draftSteps);
    return titleChange ? [titleChange, ...stepChanges] : stepChanges;
  }, [draftSteps, draftTitle, editing, steps, title]);

  const startEdit = () => {
    setDraftTitle(title ?? '');
    setDraftSteps(steps.map((step) => ({ ...step })));
    setEditing(true);
  };

  const cancelEdit = () => {
    setEditing(false);
    setDraftTitle(title ?? '');
    setDraftSteps(steps);
  };

  const saveEdit = async () => {
    if (!onSaveSteps) {
      return;
    }
    await onSaveSteps(draftSteps, draftTitle.trim() || undefined);
    setEditing(false);
  };

  return (
    <div className="plan-card" data-plan-id={planId}>
      <header className="plan-card-header">
        <span className="plan-card-badge">任务列表</span>
        {editing ? (
          <input
            className="plan-card-title-input"
            value={draftTitle}
            placeholder="计划标题"
            onChange={(event) => setDraftTitle(event.target.value)}
          />
        ) : (
          title && <h4 className="plan-card-title">{title}</h4>
        )}
        {canEdit && !editing && (
          <button type="button" className="btn ghost sm plan-edit-btn" onClick={startEdit}>
            编辑
          </button>
        )}
      </header>
      <ol className="plan-step-list">
        {stepRows.map((step, index) => (
          <li
            key={step.id}
            className={`plan-step${step.status === 'active' ? ' active' : ''}${step.status === 'done' ? ' done' : ''}`}
          >
            <span className="plan-step-index" aria-hidden>{index + 1}</span>
            {editing ? (
              <input
                className="plan-step-title-input"
                value={step.title}
                onChange={(event) => {
                  const next = [...draftSteps];
                  next[index] = { ...step, title: event.target.value };
                  setDraftSteps(next);
                }}
              />
            ) : (
              <span className="plan-step-title">{step.title}</span>
            )}
          </li>
        ))}
      </ol>
      {editing && editDiff.length > 0 && (
        <ul className="plan-diff-list" aria-label="计划变更预览">
          {editDiff.map((change, index) => (
            <li key={`${change.type}-${index}`} className={`plan-diff-item plan-diff-${change.type}`}>
              {change.type === 'added' && <>+ {change.title}</>}
              {change.type === 'removed' && <>− {change.title}</>}
              {change.type === 'changed' && (
                <>
                  ~ {change.from} → {change.to}
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {editing && (
        <footer className="plan-card-footer plan-card-edit-footer">
          <button type="button" className="btn ghost sm" onClick={cancelEdit} disabled={saving}>
            取消
          </button>
          <button type="button" className="btn primary sm" onClick={() => void saveEdit()} disabled={saving}>
            {saving ? '保存中…' : '保存计划'}
          </button>
        </footer>
      )}
      {!confirmed && onConfirm && !editing && (
        <footer className="plan-card-footer">
          <button
            type="button"
            className="btn primary plan-confirm-btn"
            disabled={confirming}
            onClick={onConfirm}
          >
            {confirming ? '切换中…' : '开始执行'}
          </button>
          <p className="plan-card-hint">确认后将切换为 Craft 模式，可写入文件与执行命令</p>
        </footer>
      )}
      {confirmed && (
        <p className="plan-card-hint confirmed">已切换 Craft 模式，可继续发送指令执行计划</p>
      )}
    </div>
  );
}
