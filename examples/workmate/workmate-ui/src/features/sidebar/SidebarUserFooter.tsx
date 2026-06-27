import { t } from '../../lib/i18n';
import { useSettings } from '../settings/SettingsProvider';

interface SidebarUserFooterProps {
  onOpenSettings?: () => void;
  onOpenAudit?: () => void;
  onOpenDev?: () => void;
  settingsActive?: boolean;
  auditActive?: boolean;
  devActive?: boolean;
}

/** 对标 mockup 底部用户区 */
export function SidebarUserFooter({
  onOpenSettings,
  onOpenAudit,
  onOpenDev,
  settingsActive = false,
  auditActive = false,
  devActive = false,
}: SidebarUserFooterProps) {
  const { settings } = useSettings();
  void settings.language;
  return (
    <footer className="sidebar-user-footer">
      {onOpenSettings && (
        <button
          type="button"
          className={`sidebar-security-link${settingsActive ? ' active' : ''}`}
          onClick={onOpenSettings}
          aria-current={settingsActive ? 'page' : undefined}
        >
          {t('sidebar.settings')}
        </button>
      )}
      {onOpenDev && (
        <button
          type="button"
          className={`sidebar-security-link${devActive ? ' active' : ''}`}
          onClick={onOpenDev}
          aria-current={devActive ? 'page' : undefined}
        >
          开发者控制台
        </button>
      )}
      {onOpenAudit && (
        <button
          type="button"
          className={`sidebar-security-link${auditActive ? ' active' : ''}`}
          onClick={onOpenAudit}
          aria-current={auditActive ? 'page' : undefined}
        >
          安全中心 · 审计日志
        </button>
      )}
      <button type="button" className="sidebar-user-card" disabled title="v0.3 — 账户">
        <span className="sidebar-user-avatar" aria-hidden>张</span>
        <span className="sidebar-user-text">
          <span className="sidebar-user-name">张小明</span>
          <span className="sidebar-user-plan">个人版</span>
        </span>
        <span className="sidebar-user-chevron" aria-hidden>▼</span>
      </button>
    </footer>
  );
}
