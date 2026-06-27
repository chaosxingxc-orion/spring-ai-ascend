import { describe, expect, it } from 'vitest';
import {
  buildCronExpression,
  canAdvanceWizardStep,
  describeCronSchedule,
  DEFAULT_CRON_SCHEDULE,
  DEFAULT_CRON_TASK,
} from './cronWizard';

describe('cronWizard', () => {
  it('builds daily cron', () => {
    expect(
      buildCronExpression({ ...DEFAULT_CRON_SCHEDULE, preset: 'daily', hour: 9, minute: 30 }),
    ).toBe('30 9 * * *');
  });

  it('builds weekdays cron', () => {
    expect(
      buildCronExpression({ ...DEFAULT_CRON_SCHEDULE, preset: 'weekdays', hour: 8, minute: 0 }),
    ).toBe('0 8 * * MON-FRI');
  });

  it('builds weekly cron', () => {
    expect(
      buildCronExpression({ ...DEFAULT_CRON_SCHEDULE, preset: 'weekly', weekday: 3, hour: 10, minute: 15 }),
    ).toBe('15 10 * * WED');
  });

  it('describes schedule in Chinese', () => {
    expect(describeCronSchedule({ ...DEFAULT_CRON_SCHEDULE, preset: 'daily', hour: 9, minute: 0 })).toBe(
      '每天 09:00',
    );
    expect(
      describeCronSchedule({ ...DEFAULT_CRON_SCHEDULE, preset: 'weekly', weekday: 1, hour: 9, minute: 0 }),
    ).toBe('每周周一 09:00');
  });

  it('validates wizard steps', () => {
    const draft = { schedule: DEFAULT_CRON_SCHEDULE, task: DEFAULT_CRON_TASK };
    expect(canAdvanceWizardStep('schedule', draft)).toBe(true);
    expect(canAdvanceWizardStep('task', draft)).toBe(false);
    expect(
      canAdvanceWizardStep('task', {
        ...draft,
        task: { name: '日报', promptText: '摘要', expertId: '' },
      }),
    ).toBe(true);
  });
});
