import type { ReactNode } from 'react';

export interface NavShellAction {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary';
}

export interface NavShellPageProps {
  icon: string;
  title: string;
  subtitle: string;
  description: string;
  bullets?: string[];
  badge?: string;
  actions?: NavShellAction[];
  children?: ReactNode;
}

/** 侧栏 Nav 未完全落地能力的统一壳层（对齐 settings / files 双栏主区） */
export function NavShellPage({
  icon,
  title,
  subtitle,
  description,
  bullets = [],
  badge = '规划中',
  actions = [],
  children,
}: NavShellPageProps) {
  return (
    <main className="nav-shell-page">
      <header className="nav-shell-header">
        <div className="nav-shell-title-row">
          <span className="nav-shell-icon" aria-hidden>
            {icon}
          </span>
          <div>
            <p className="nav-shell-eyebrow">{subtitle}</p>
            <h1>{title}</h1>
          </div>
          {badge && <span className="nav-shell-badge">{badge}</span>}
        </div>
        <p className="nav-shell-desc">{description}</p>
        {bullets.length > 0 && (
          <ul className="nav-shell-bullets">
            {bullets.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        )}
        {actions.length > 0 && (
          <div className="nav-shell-actions">
            {actions.map((action) => (
              <button
                key={action.label}
                type="button"
                className={`btn ${action.variant === 'secondary' ? 'secondary' : 'primary'}`}
                onClick={action.onClick}
              >
                {action.label}
              </button>
            ))}
          </div>
        )}
      </header>
      {children}
    </main>
  );
}
