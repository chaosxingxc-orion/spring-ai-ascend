import type { PlanStep } from '../types/events';

export type PlanStepChange =
  | { type: 'added'; title: string }
  | { type: 'removed'; title: string }
  | { type: 'changed'; from: string; to: string };

export function diffPlanSteps(before: PlanStep[], after: PlanStep[]): PlanStepChange[] {
  const changes: PlanStepChange[] = [];
  const beforeById = new Map(before.map((step) => [step.id, step]));
  const afterById = new Map(after.map((step) => [step.id, step]));

  for (const step of after) {
    const prev = beforeById.get(step.id);
    if (!prev) {
      changes.push({ type: 'added', title: step.title });
      continue;
    }
    if (prev.title.trim() !== step.title.trim()) {
      changes.push({ type: 'changed', from: prev.title, to: step.title });
    }
  }

  for (const step of before) {
    if (!afterById.has(step.id)) {
      changes.push({ type: 'removed', title: step.title });
    }
  }

  return changes;
}

export function diffPlanTitle(before: string | undefined, after: string | undefined): PlanStepChange | null {
  const from = (before ?? '').trim();
  const to = (after ?? '').trim();
  if (from === to) {
    return null;
  }
  if (!from && to) {
    return { type: 'added', title: `标题：${to}` };
  }
  if (from && !to) {
    return { type: 'removed', title: `标题：${from}` };
  }
  return { type: 'changed', from, to };
}
