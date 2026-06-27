import { useState } from 'react';
import type { WelcomeOnboardingConfig, WelcomeSampleTask } from '../../types/welcome';

export const ONBOARDING_DONE_KEY = 'workmate.onboarding.done.v1';
export const ONBOARDING_PROFILE_KEY = 'workmate.onboarding.profile.v1';

export interface OnboardingProfile {
  role: string;
  interests: string[];
}

export function loadOnboardingProfile(): OnboardingProfile | null {
  try {
    const raw = localStorage.getItem(ONBOARDING_PROFILE_KEY);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as OnboardingProfile;
  } catch {
    return null;
  }
}

export function saveOnboardingProfile(profile: OnboardingProfile): void {
  localStorage.setItem(ONBOARDING_PROFILE_KEY, JSON.stringify(profile));
}

interface OnboardingFlowProps {
  config: WelcomeOnboardingConfig;
  onComplete: (payload: { role: string; interests: string[]; sampleTask: WelcomeSampleTask }) => void;
  onSkip: () => void;
}

export function OnboardingFlow({ config, onComplete, onSkip }: OnboardingFlowProps) {
  const [step, setStep] = useState(0);
  const [role, setRole] = useState('');
  const [interests, setInterests] = useState<string[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState(config.sampleTasks[0]?.id ?? '');

  const toggleInterest = (id: string) => {
    setInterests((prev) => (prev.includes(id) ? prev.filter((item) => item !== id) : [...prev, id]));
  };

  const selectedTask =
    config.sampleTasks.find((task) => task.id === selectedTaskId) ?? config.sampleTasks[0];

  const titles = [config.step1Title, config.step2Title, config.step3Title];
  const hints = [config.step1Hint, config.step2Hint, config.step3Hint];

  return (
    <div className="onboarding-overlay" role="dialog" aria-modal="true" aria-labelledby="onboarding-title">
      <div className="onboarding-card">
        <header>
          <p className="onboarding-step">步骤 {step + 1} / 3</p>
          <h2 id="onboarding-title">{titles[step] ?? '欢迎使用 WorkMate'}</h2>
          {hints[step] && <p className="onboarding-hint">{hints[step]}</p>}
        </header>

        {step === 0 && (
          <label className="settings-field">
            <span className="settings-field-label">你的角色</span>
            <input
              type="text"
              value={role}
              placeholder="例如：产品经理、研发工程师"
              onChange={(event) => setRole(event.target.value)}
            />
          </label>
        )}

        {step === 1 && (
          <div className="onboarding-tags">
            {config.interests.map((tag) => (
              <button
                key={tag.id}
                type="button"
                className={`market-pill${interests.includes(tag.id) ? ' active' : ''}`}
                onClick={() => toggleInterest(tag.id)}
              >
                {tag.label}
              </button>
            ))}
          </div>
        )}

        {step === 2 && (
          <div className="onboarding-samples">
            {config.sampleTasks.map((task) => (
              <button
                key={task.id}
                type="button"
                className={`onboarding-sample${selectedTaskId === task.id ? ' active' : ''}`}
                onClick={() => setSelectedTaskId(task.id)}
              >
                <strong>{task.title}</strong>
                <span>{task.prompt}</span>
              </button>
            ))}
          </div>
        )}

        <footer className="onboarding-actions">
          <button type="button" className="btn ghost" onClick={onSkip}>
            跳过
          </button>
          {step > 0 && (
            <button type="button" className="btn ghost" onClick={() => setStep((value) => value - 1)}>
              上一步
            </button>
          )}
          {step < 2 ? (
            <button
              type="button"
              className="btn primary"
              disabled={step === 0 && !role.trim()}
              onClick={() => setStep((value) => value + 1)}
            >
              下一步
            </button>
          ) : (
            <button
              type="button"
              className="btn primary"
              disabled={!selectedTask}
              onClick={() => {
                if (selectedTask) {
                  onComplete({ role: role.trim(), interests, sampleTask: selectedTask });
                }
              }}
            >
              开始首个任务
            </button>
          )}
        </footer>
      </div>
    </div>
  );
}
