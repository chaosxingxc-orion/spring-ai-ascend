import type { CSSProperties, ReactNode } from 'react';

interface WorkbenchLayoutProps {
  variant: 'new-task' | 'session' | 'market' | 'audit' | 'share';
  children: ReactNode;
  shellClassName?: string;
  shellStyle?: CSSProperties;
}

/**
 * 工作台主区布局壳 — 控制 grid 列宽与页面级 class
 * @see docs/frontend-architecture.md
 */
export function WorkbenchLayout({
  variant,
  children,
  shellClassName,
  shellStyle,
}: WorkbenchLayoutProps) {
  const shellClass = [
    'app-shell',
    variant === 'market' && 'app-shell-market',
    variant === 'new-task' && 'app-shell-new',
    variant === 'audit' && 'app-shell-audit',
    variant === 'share' && 'app-shell-share',
    shellClassName,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={shellClass} style={shellStyle}>
      {children}
    </div>
  );
}
