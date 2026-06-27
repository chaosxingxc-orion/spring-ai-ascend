import { describe, expect, it } from 'vitest';
import { diffPlanSteps, diffPlanTitle } from './planDiff';

describe('planDiff', () => {
  it('detects added, removed, and changed steps', () => {
    const before = [
      { id: 'a', title: '调研' },
      { id: 'b', title: '起草' },
    ];
    const after = [
      { id: 'a', title: '调研市场' },
      { id: 'c', title: '评审' },
    ];
    expect(diffPlanSteps(before, after)).toEqual([
      { type: 'changed', from: '调研', to: '调研市场' },
      { type: 'added', title: '评审' },
      { type: 'removed', title: '起草' },
    ]);
  });

  it('detects title changes', () => {
    expect(diffPlanTitle('旧标题', '新标题')).toEqual({
      type: 'changed',
      from: '旧标题',
      to: '新标题',
    });
  });
});
