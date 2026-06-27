import { useMemo, useState } from 'react';
import type { Expert } from '../../types/api';
import { resolveExpertDisplayName } from '../../lib/teamUiLabels';
import {
  buildCronExpression,
  canAdvanceWizardStep,
  CRON_PRESET_OPTIONS,
  CRON_WIZARD_STEPS,
  DEFAULT_CRON_SCHEDULE,
  DEFAULT_CRON_TASK,
  describeCronSchedule,
  type CronWizardDraft,
  type CronWizardStep,
  WEEKDAY_LABELS,
  wizardStepIndex,
} from '../../lib/cronWizard';

export interface AutomationJobWizardSubmit {
  name: string;
  promptText: string;
  expertId?: string;
  cronExpression: string;
}

interface AutomationJobWizardProps {
  experts: Expert[];
  busy?: boolean;
  onSubmit: (payload: AutomationJobWizardSubmit) => Promise<void>;
  onCancel?: () => void;
}

export function AutomationJobWizard({ experts, busy = false, onSubmit, onCancel }: AutomationJobWizardProps) {
  const [step, setStep] = useState<CronWizardStep>('schedule');
  const [draft, setDraft] = useState<CronWizardDraft>({
    schedule: DEFAULT_CRON_SCHEDULE,
    task: DEFAULT_CRON_TASK,
  });

  const cronExpression = useMemo(() => buildCronExpression(draft.schedule), [draft.schedule]);
  const scheduleLabel = useMemo(() => describeCronSchedule(draft.schedule), [draft.schedule]);
  const expert = experts.find((item) => item.id === draft.task.expertId);
  const expertLabel = expert ? resolveExpertDisplayName(expert) : '默认单 Agent';
  const activeIndex = wizardStepIndex(step);

  const updateSchedule = (patch: Partial<CronWizardDraft['schedule']>) => {
    setDraft((prev) => ({ ...prev, schedule: { ...prev.schedule, ...patch } }));
  };

  const updateTask = (patch: Partial<CronWizardDraft['task']>) => {
    setDraft((prev) => ({ ...prev, task: { ...prev.task, ...patch } }));
  };

  const goNext = () => {
    if (step === 'schedule') {
      setStep('task');
      return;
    }
    if (step === 'task') {
      setStep('confirm');
    }
  };

  const goBack = () => {
    if (step === 'confirm') {
      setStep('task');
      return;
    }
    if (step === 'task') {
      setStep('schedule');
    }
  };

  const handleSubmit = async () => {
    if (!canAdvanceWizardStep('confirm', draft)) {
      return;
    }
    await onSubmit({
      name: draft.task.name.trim(),
      promptText: draft.task.promptText.trim(),
      expertId: draft.task.expertId || undefined,
      cronExpression,
    });
    setStep('schedule');
    setDraft({ schedule: DEFAULT_CRON_SCHEDULE, task: DEFAULT_CRON_TASK });
  };

  return (
    <div className="automation-job-wizard" aria-label="创建定时任务向导">
      <ol className="connector-oauth-walkthrough automation-wizard-steps" aria-label="向导步骤">
        {CRON_WIZARD_STEPS.map((item, index) => {
          const state = index < activeIndex ? 'done' : index === activeIndex ? 'current' : 'pending';
          return (
            <li key={item.id} className={`connector-oauth-step connector-oauth-step-${state}`}>
              <span className="connector-oauth-step-index" aria-hidden>
                {index + 1}
              </span>
              <div>
                <strong>{item.title}</strong>
                <p className="market-hint">{item.detail}</p>
              </div>
            </li>
          );
        })}
      </ol>

      {step === 'schedule' && (
        <div className="automation-wizard-panel">
          <fieldset className="automation-cron-presets">
            <legend>运行频率</legend>
            <div className="automation-cron-preset-grid">
              {CRON_PRESET_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  className={`automation-cron-preset${draft.schedule.preset === option.id ? ' active' : ''}`}
                  aria-pressed={draft.schedule.preset === option.id}
                  onClick={() => updateSchedule({ preset: option.id })}
                >
                  <strong>{option.label}</strong>
                  <span className="muted">{option.hint}</span>
                </button>
              ))}
            </div>
          </fieldset>

          {draft.schedule.preset !== 'custom' && draft.schedule.preset !== 'hourly' && (
            <div className="automation-cron-time-row">
              <label className="connector-connect-field">
                <span>时刻</span>
                <input
                  type="time"
                  className="connector-connect-input"
                  value={`${String(draft.schedule.hour).padStart(2, '0')}:${String(draft.schedule.minute).padStart(2, '0')}`}
                  onChange={(event) => {
                    const [hour, minute] = event.target.value.split(':').map((part) => Number(part));
                    updateSchedule({ hour: hour ?? 9, minute: minute ?? 0 });
                  }}
                />
              </label>
            </div>
          )}

          {draft.schedule.preset === 'hourly' && (
            <label className="connector-connect-field">
              <span>每小时第几分钟</span>
              <input
                type="number"
                min={0}
                max={59}
                className="connector-connect-input"
                value={draft.schedule.minute}
                onChange={(event) => updateSchedule({ minute: Number(event.target.value) })}
              />
            </label>
          )}

          {draft.schedule.preset === 'weekly' && (
            <label className="connector-connect-field">
              <span>星期</span>
              <select
                className="connector-connect-input"
                value={draft.schedule.weekday}
                onChange={(event) => updateSchedule({ weekday: Number(event.target.value) })}
              >
                {WEEKDAY_LABELS.map((label, index) => (
                  <option key={label} value={index}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          )}

          {draft.schedule.preset === 'custom' && (
            <label className="connector-connect-field">
              <span>Cron 表达式（5 或 6 字段）</span>
              <input
                className="connector-connect-input mono"
                value={draft.schedule.customExpression}
                onChange={(event) => updateSchedule({ customExpression: event.target.value })}
                placeholder="0 9 * * *"
              />
            </label>
          )}

          <p className="automation-cron-preview muted" role="status">
            预览：{scheduleLabel} · <code>{cronExpression}</code>
          </p>
        </div>
      )}

      {step === 'task' && (
        <div className="automation-wizard-panel">
          <label className="connector-connect-field">
            <span>任务名称</span>
            <input
              className="connector-connect-input"
              value={draft.task.name}
              onChange={(event) => updateTask({ name: event.target.value })}
              placeholder="例如：每日研报"
            />
          </label>
          <label className="connector-connect-field">
            <span>专家（可选）</span>
            <select
              className="connector-connect-input"
              value={draft.task.expertId}
              onChange={(event) => updateTask({ expertId: event.target.value })}
            >
              <option value="">默认单 Agent</option>
              {experts.map((item) => (
                <option key={item.id} value={item.id}>
                  {resolveExpertDisplayName(item)}
                </option>
              ))}
            </select>
          </label>
          <label className="connector-connect-field">
            <span>Prompt</span>
            <textarea
              className="connector-connect-input automation-prompt-input"
              rows={4}
              value={draft.task.promptText}
              onChange={(event) => updateTask({ promptText: event.target.value })}
              placeholder="定时发送给 Agent 的指令"
            />
          </label>
        </div>
      )}

      {step === 'confirm' && (
        <div className="automation-wizard-panel automation-wizard-summary">
          <dl className="automation-wizard-summary-list">
            <div>
              <dt>任务名称</dt>
              <dd>{draft.task.name.trim()}</dd>
            </div>
            <div>
              <dt>专家</dt>
              <dd>{expertLabel}</dd>
            </div>
            <div>
              <dt>执行计划</dt>
              <dd>{scheduleLabel}</dd>
            </div>
            <div>
              <dt>Cron</dt>
              <dd className="mono">{cronExpression}</dd>
            </div>
            <div>
              <dt>Prompt</dt>
              <dd className="automation-wizard-summary-prompt">{draft.task.promptText.trim()}</dd>
            </div>
          </dl>
          <p className="muted automation-wizard-summary-hint">
            确认后将创建定时任务；每次触发会新建会话并发送上述 Prompt。
          </p>
        </div>
      )}

      <div className="automation-wizard-actions">
        {onCancel && step === 'schedule' && (
          <button type="button" className="btn ghost" disabled={busy} onClick={onCancel}>
            取消
          </button>
        )}
        {step !== 'schedule' && (
          <button type="button" className="btn ghost" disabled={busy} onClick={goBack}>
            上一步
          </button>
        )}
        {step !== 'confirm' && (
          <button
            type="button"
            className="btn primary"
            disabled={busy || !canAdvanceWizardStep(step, draft)}
            onClick={goNext}
          >
            下一步
          </button>
        )}
        {step === 'confirm' && (
          <button
            type="button"
            className="btn primary"
            disabled={busy || !canAdvanceWizardStep('confirm', draft)}
            onClick={() => void handleSubmit()}
          >
            {busy ? '创建中…' : '创建定时任务'}
          </button>
        )}
      </div>
    </div>
  );
}
