export type CronPreset = 'daily' | 'weekdays' | 'weekly' | 'hourly' | 'custom';

export type CronWizardStep = 'schedule' | 'task' | 'confirm';

export interface CronWizardSchedule {
  preset: CronPreset;
  hour: number;
  minute: number;
  /** 0 = Sunday … 6 = Saturday */
  weekday: number;
  customExpression: string;
}

export interface CronWizardTask {
  name: string;
  promptText: string;
  expertId: string;
}

export interface CronWizardDraft {
  schedule: CronWizardSchedule;
  task: CronWizardTask;
}

export const CRON_WIZARD_STEPS: { id: CronWizardStep; title: string; detail: string }[] = [
  { id: 'schedule', title: '执行计划', detail: '选择 Cron 频率与时间' },
  { id: 'task', title: '任务内容', detail: '名称、专家与 Prompt' },
  { id: 'confirm', title: '确认创建', detail: '核对摘要后提交' },
];

export const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const;

export const CRON_PRESET_OPTIONS: { id: CronPreset; label: string; hint: string }[] = [
  { id: 'daily', label: '每天', hint: '在固定时刻运行' },
  { id: 'weekdays', label: '工作日', hint: '周一至周五' },
  { id: 'weekly', label: '每周', hint: '指定星期几' },
  { id: 'hourly', label: '每小时', hint: '每小时的固定分钟' },
  { id: 'custom', label: '自定义', hint: '5 或 6 字段 Cron' },
];

const WEEKDAY_CRON = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'] as const;

export const DEFAULT_CRON_SCHEDULE: CronWizardSchedule = {
  preset: 'daily',
  hour: 9,
  minute: 0,
  weekday: 1,
  customExpression: '0 9 * * *',
};

export const DEFAULT_CRON_TASK: CronWizardTask = {
  name: '',
  promptText: '',
  expertId: '',
};

export function buildCronExpression(schedule: CronWizardSchedule): string {
  const minute = clamp(schedule.minute, 0, 59);
  const hour = clamp(schedule.hour, 0, 23);
  switch (schedule.preset) {
    case 'daily':
      return `${minute} ${hour} * * *`;
    case 'weekdays':
      return `${minute} ${hour} * * MON-FRI`;
    case 'weekly': {
      const day = WEEKDAY_CRON[clamp(schedule.weekday, 0, 6)] ?? 'MON';
      return `${minute} ${hour} * * ${day}`;
    }
    case 'hourly':
      return `${minute} * * * *`;
    case 'custom':
      return schedule.customExpression.trim() || '0 9 * * *';
    default:
      return '0 9 * * *';
  }
}

export function describeCronSchedule(schedule: CronWizardSchedule): string {
  const time = formatClock(schedule.hour, schedule.minute);
  switch (schedule.preset) {
    case 'daily':
      return `每天 ${time}`;
    case 'weekdays':
      return `工作日 ${time}`;
    case 'weekly':
      return `每周${WEEKDAY_LABELS[clamp(schedule.weekday, 0, 6)]} ${time}`;
    case 'hourly':
      return `每小时第 ${clamp(schedule.minute, 0, 59)} 分钟`;
    case 'custom':
      return `自定义 Cron：${buildCronExpression(schedule)}`;
    default:
      return buildCronExpression(schedule);
  }
}

export function canAdvanceWizardStep(step: CronWizardStep, draft: CronWizardDraft): boolean {
  switch (step) {
    case 'schedule':
      if (draft.schedule.preset === 'custom') {
        return draft.schedule.customExpression.trim().length > 0;
      }
      return true;
    case 'task':
      return draft.task.name.trim().length > 0 && draft.task.promptText.trim().length > 0;
    case 'confirm':
      return canAdvanceWizardStep('schedule', draft) && canAdvanceWizardStep('task', draft);
    default:
      return false;
  }
}

export function wizardStepIndex(step: CronWizardStep): number {
  return CRON_WIZARD_STEPS.findIndex((item) => item.id === step);
}

function formatClock(hour: number, minute: number): string {
  return `${String(clamp(hour, 0, 23)).padStart(2, '0')}:${String(clamp(minute, 0, 59)).padStart(2, '0')}`;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
