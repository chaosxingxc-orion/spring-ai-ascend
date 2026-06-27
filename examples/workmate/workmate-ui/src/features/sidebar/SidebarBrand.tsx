interface SidebarBrandProps {
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

export function SidebarBrand({
  collapsed = false,
  onToggleCollapse,
}: SidebarBrandProps) {
  return (
    <div className="sidebar-brand-row">
      <div className="sidebar-brand">
        <span className="sidebar-logo" aria-hidden>W</span>
        <h1>WorkMate</h1>
        <span className="sidebar-version">v0.3</span>
      </div>
      <button
        type="button"
        className={`sidebar-collapse-btn${collapsed ? ' active' : ''}`}
        title={collapsed ? '展开侧栏' : '折叠侧栏'}
        aria-label={collapsed ? '展开侧栏' : '折叠侧栏'}
        aria-pressed={collapsed}
        onClick={onToggleCollapse}
      >
        ≡
      </button>
    </div>
  );
}
