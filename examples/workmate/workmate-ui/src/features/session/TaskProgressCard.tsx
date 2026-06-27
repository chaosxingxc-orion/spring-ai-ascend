import { findActivePlan } from '../../state/chatState';
import type { ChatItem } from '../../types/events';
import { PlanCard } from './PlanCard';

interface TaskProgressCardProps {
  items: ChatItem[];
  visibleItems?: ChatItem[];
  onConfirmPlan?: (planId: string) => void;
  confirmingPlan?: boolean;
  onUpdatePlanSteps?: (planId: string, steps: import('../../types/events').PlanStep[], title?: string) => void | Promise<void>;
  savingPlan?: boolean;
}

/** Plan 卡（工具步骤见 DeepThinkingTrace） */
export function TaskProgressCard({
  items,
  visibleItems,
  onConfirmPlan,
  confirmingPlan = false,
  onUpdatePlanSteps,
  savingPlan = false,
}: TaskProgressCardProps) {
  const activePlan = findActivePlan(items);

  if (!activePlan || activePlan.kind !== 'plan') {
    return null;
  }

  // Avoid duplicate plan cards: when the active plan is already visible inline
  // in the timeline, keep only the inline copy.
  const visibleSource = visibleItems ?? items;
  const planAlreadyInline = visibleSource.some(
    (item) => item.kind === 'plan' && item.planId === activePlan.planId && !item.confirmed,
  );
  if (planAlreadyInline) {
    return null;
  }

  return (
    <div className="task-progress-card" aria-live="polite">
      <div className="task-progress-meta">
        <span className="task-deep-thinking">深度思考</span>
      </div>
      <PlanCard
        planId={activePlan.planId}
        title={activePlan.title}
        steps={activePlan.steps}
        confirmed={activePlan.confirmed}
        confirming={confirmingPlan}
        saving={savingPlan}
        onConfirm={() => onConfirmPlan?.(activePlan.planId)}
        onSaveSteps={
          onUpdatePlanSteps
            ? (steps, title) => onUpdatePlanSteps(activePlan.planId, steps, title)
            : undefined
        }
      />
    </div>
  );
}
