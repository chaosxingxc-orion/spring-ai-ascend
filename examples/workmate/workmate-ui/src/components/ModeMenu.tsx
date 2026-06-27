import type { PermissionMode } from '../types/api';

/** S05 默认权限占位 — 与 Craft/Ask/Plan 交互模式分离 */
interface ModeMenuProps {
  value: PermissionMode;
  disabled?: boolean;
  readOnly?: boolean;
}

export function permissionModeLabel(mode: PermissionMode | undefined): string {
  switch (mode) {
    case 'ASK':
      return 'Ask';
    case 'PLAN':
      return 'Plan';
    default:
      return 'Craft';
  }
}

export function ModeMenu({ disabled, readOnly }: ModeMenuProps) {
  if (readOnly) {
    return (
      <span className="dock-pill dock-pill-static" title="会话权限（创建时固定）">
        <span className="dock-pill-icon" aria-hidden>✓</span>
        默认权限
      </span>
    );
  }

  return (
    <button
      type="button"
      className="dock-pill dock-pill-muted"
      disabled={disabled}
      title="v0.3 — 权限策略（默认 / 完全访问）"
    >
      <span className="dock-pill-icon" aria-hidden>✓</span>
      默认权限 ▾
    </button>
  );
}
