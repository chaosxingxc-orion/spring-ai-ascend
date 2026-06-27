import { NavLink } from 'react-router-dom';
import { DEV_AGENTS_PATH, DEV_PLAYBOOKS_PATH, DEV_RUNTIME_PATH, DEV_SKILLS_PATH, DEV_TEAMS_PATH, DEV_WELCOME_PATH } from '../../lib/paths';

export function DevStudioNav() {
  return (
    <nav className="dev-studio-nav" aria-label="开发者控制台">
      <NavLink
        to={DEV_AGENTS_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        专家
      </NavLink>
      <NavLink
        to={DEV_TEAMS_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        专家团
      </NavLink>
      <NavLink
        to={DEV_SKILLS_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        技能
      </NavLink>
      <NavLink
        to={DEV_WELCOME_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        Welcome
      </NavLink>
      <NavLink
        to={DEV_PLAYBOOKS_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        Playbook
      </NavLink>
      <NavLink
        to={DEV_RUNTIME_PATH}
        end
        className={({ isActive }) => `dev-studio-nav-item${isActive ? ' active' : ''}`}
      >
        运行时
      </NavLink>
    </nav>
  );
}
