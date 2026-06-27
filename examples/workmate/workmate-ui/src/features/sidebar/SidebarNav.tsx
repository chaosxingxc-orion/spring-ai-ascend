import { NavLink } from 'react-router-dom';
import { SIDEBAR_NAV_ITEMS, resolveSidebarNavId } from './sidebarNavConfig';

interface SidebarNavProps {
  pathname: string;
}

export function SidebarNav({ pathname }: SidebarNavProps) {
  const activeId = resolveSidebarNavId(pathname);

  return (
    <nav className="sidebar-nav-list" aria-label="主导航">
      {SIDEBAR_NAV_ITEMS.map((item) => {
        const active = activeId === item.id;
        const implemented = item.implemented !== false;
        const className = `sidebar-nav-item${active ? ' active' : ''}${implemented ? '' : ' is-unimplemented'}`;

        if (!implemented) {
          return (
            <button
              key={item.id}
              type="button"
              className={className}
              disabled
              title="即将推出"
              aria-disabled
            >
              <span className="sidebar-nav-icon" aria-hidden>
                {item.icon}
              </span>
              <span className="sidebar-nav-text">
                <span className="sidebar-nav-label">{item.label}</span>
                <span className="sidebar-nav-sub">{item.sub}</span>
              </span>
            </button>
          );
        }

        return (
          <NavLink key={item.id} to={item.path} className={className}>
            <span className="sidebar-nav-icon" aria-hidden>
              {item.icon}
            </span>
            <span className="sidebar-nav-text">
              <span className="sidebar-nav-label">{item.label}</span>
              <span className="sidebar-nav-sub">{item.sub}</span>
            </span>
          </NavLink>
        );
      })}
    </nav>
  );
}
