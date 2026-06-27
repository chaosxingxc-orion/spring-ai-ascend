import type { WelcomeConfig } from '../types/welcome';

interface MainHeaderProps {
  growthPlan?: WelcomeConfig['growthPlan'];
}

/** 主内容区顶栏 */
export function MainHeader({ growthPlan }: MainHeaderProps) {
  const showGrowthPlan = growthPlan?.enabled && growthPlan?.label;

  return (
    <header className="main-header" aria-label="页面操作">
      {showGrowthPlan && (
        <button
          type="button"
          className="growth-plan-cta"
          disabled={!growthPlan?.enabled}
          title={growthPlan?.enabled ? undefined : 'v0.3 — 成长计划'}
        >
          {growthPlan.label} ›
        </button>
      )}
      <div className="main-header-actions">
        <button type="button" className="main-header-btn" disabled title="v0.3 — 通知">
          <span aria-hidden>🔔</span>
        </button>
        <button type="button" className="main-header-btn" disabled title="v0.3 — 帮助">
          <span aria-hidden>?</span>
        </button>
        <button type="button" className="main-header-user" disabled title="v0.3 — 账户">
          Z
        </button>
      </div>
    </header>
  );
}
