import { useCallback, useEffect, useState } from 'react';
import type { Expert } from '../../types/api';
import {
  createAutomationJob,
  deleteAutomationJob,
  listAutomationJobs,
  runAutomationJobNow,
  updateAutomationJob,
  type AutomationJob,
} from '../../api/automation';
import { formatRelativeTime } from '../../lib/formatRelativeTime';
import { AutomationJobWizard } from './AutomationJobWizard';

interface AutomationJobsPanelProps {
  experts: Expert[];
  onOpenSession: (sessionId: string) => void;
}

export function AutomationJobsPanel({ experts, onOpenSession }: AutomationJobsPanelProps) {
  const [jobs, setJobs] = useState<AutomationJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [wizardOpen, setWizardOpen] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setJobs(await listAutomationJobs());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const handleCreate = async (payload: {
    name: string;
    promptText: string;
    expertId?: string;
    cronExpression: string;
  }) => {
    setBusyId('create');
    setError(null);
    try {
      await createAutomationJob(payload);
      setWizardOpen(false);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleToggle = async (job: AutomationJob) => {
    setBusyId(job.id);
    try {
      await updateAutomationJob(job.id, { enabled: !job.enabled });
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleRun = async (job: AutomationJob) => {
    setBusyId(job.id);
    try {
      const updated = await runAutomationJobNow(job.id);
      await refresh();
      if (updated.lastSessionId) {
        onOpenSession(updated.lastSessionId);
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleDelete = async (job: AutomationJob) => {
    if (!window.confirm(`删除定时任务「${job.name}」？`)) {
      return;
    }
    setBusyId(job.id);
    try {
      await deleteAutomationJob(job.id);
      await refresh();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="nav-shell-section automation-jobs-panel" aria-label="定时任务">
      <div className="automation-jobs-panel-head">
        <div>
          <h2 className="nav-shell-section-title">定时任务</h2>
          <p className="settings-panel-desc">
            通过向导配置 Cron 计划与专家；每次运行会新建会话并发送 prompt。
          </p>
        </div>
        {!wizardOpen && (
          <button type="button" className="btn primary sm" onClick={() => setWizardOpen(true)}>
            新建定时任务
          </button>
        )}
      </div>

      {error && <p className="market-hint error" role="alert">{error}</p>}

      {wizardOpen && (
        <AutomationJobWizard
          experts={experts}
          busy={busyId === 'create'}
          onSubmit={handleCreate}
          onCancel={() => setWizardOpen(false)}
        />
      )}

      {loading ? (
        <p className="nav-shell-empty muted">加载中…</p>
      ) : jobs.length === 0 ? (
        <p className="nav-shell-empty muted">暂无定时任务</p>
      ) : (
        <ul className="automation-job-list">
          {jobs.map((job) => (
            <li key={job.id} className="automation-job-card">
              <div className="automation-job-card-head">
                <strong>{job.name}</strong>
                <span className={`automation-job-status status-${job.lastStatus?.toLowerCase() ?? 'none'}`}>
                  {job.enabled ? '已启用' : '已暂停'}
                  {job.lastStatus ? ` · ${job.lastStatus}` : ''}
                </span>
              </div>
              <p className="muted automation-job-cron">Cron: {job.cronExpression}</p>
              <p className="automation-job-prompt">{job.promptText}</p>
              {job.nextRunAt && (
                <p className="muted">下次：{formatRelativeTime(job.nextRunAt)}</p>
              )}
              {job.lastSessionId && (
                <button
                  type="button"
                  className="btn ghost sm"
                  onClick={() => onOpenSession(job.lastSessionId!)}
                >
                  查看上次会话
                </button>
              )}
              {job.lastError && <p className="market-hint error">{job.lastError}</p>}
              <div className="automation-job-actions">
                <button
                  type="button"
                  className="btn secondary sm"
                  disabled={busyId === job.id}
                  onClick={() => void handleRun(job)}
                >
                  立即运行
                </button>
                <button
                  type="button"
                  className="btn ghost sm"
                  disabled={busyId === job.id}
                  onClick={() => void handleToggle(job)}
                >
                  {job.enabled ? '暂停' : '启用'}
                </button>
                <button
                  type="button"
                  className="btn danger sm"
                  disabled={busyId === job.id}
                  onClick={() => void handleDelete(job)}
                >
                  删除
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
