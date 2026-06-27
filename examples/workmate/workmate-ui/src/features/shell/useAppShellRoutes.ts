import { useEffect } from 'react';
import {
  useLocation,
  useMatch,
  useNavigate,
  useSearchParams,
} from 'react-router-dom';
import {
  AUDIT_LOG_PATH,
  AUTOMATION_PATH,
  ASSISTANT_PATH,
  isDevStudioPath,
  isLegacyPluginsMarketPath,
  isMarketPath,
  isSidebarNavShellPath,
  marketPath,
  marketTabFromPathname,
  MORE_PATH,
  MY_FILES_PATH,
  NEW_TASK_PATH,
  PROJECTS_PATH,
  SETTINGS_PATH,
} from '../../lib/paths';
import { parseExpertMarketKind, type ExpertMarketKind } from '../../lib/expertMarketFilter';
import { parseSettingsSection } from '../../views/settings/SettingsView';

export function useAppShellRoutes() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const sessionMatch = useMatch('/s/:sessionId');
  const shareMatch = useMatch('/share/:token');

  const routeSessionId = sessionMatch?.params.sessionId ?? null;
  const shareToken = shareMatch?.params.token ?? null;
  const isShareReplay = Boolean(shareToken);
  const isNewTask = location.pathname === NEW_TASK_PATH;
  const isMyFiles = location.pathname === MY_FILES_PATH;
  const isAssistantHub = location.pathname === ASSISTANT_PATH;
  const isProjectsHub = location.pathname === PROJECTS_PATH;
  const isAutomationHub = location.pathname === AUTOMATION_PATH;
  const isMoreHub = location.pathname === MORE_PATH;
  const isNavShell = isSidebarNavShellPath(location.pathname);
  const isAuditLog = location.pathname === AUDIT_LOG_PATH;
  const isDevStudio = isDevStudioPath(location.pathname);
  const isSettings =
    location.pathname === SETTINGS_PATH || location.pathname.startsWith(`${SETTINGS_PATH}/`);
  const settingsSection = (() => {
    if (!isSettings) {
      return 'general' as const;
    }
    const suffix = location.pathname.slice(SETTINGS_PATH.length).replace(/^\//, '');
    return parseSettingsSection(suffix || undefined);
  })();
  const isMarket = isMarketPath(location.pathname);
  const marketTab = marketTabFromPathname(location.pathname);
  const expertMarketQuery = searchParams.get('q') ?? '';
  const expertCategory = searchParams.get('cat') ?? '全部';
  const expertKind = parseExpertMarketKind(searchParams.get('kind'));
  const expertSort = (searchParams.get('sort') === 'newest' ? 'newest' : 'popular') as
    | 'popular'
    | 'newest';

  useEffect(() => {
    if (!isLegacyPluginsMarketPath(location.pathname)) {
      return;
    }
    navigate(marketPath('skills', expertMarketQuery || undefined, { kind: expertKind }), {
      replace: true,
    });
  }, [expertKind, expertMarketQuery, location.pathname, navigate]);

  const updateExpertQuery = (query: string) => {
    const next = new URLSearchParams(searchParams);
    if (query.trim()) {
      next.set('q', query);
    } else {
      next.delete('q');
    }
    setSearchParams(next, { replace: true });
  };

  const updateExpertCategory = (category: string) => {
    const next = new URLSearchParams(searchParams);
    if (category !== '全部') {
      next.set('cat', category);
    } else {
      next.delete('cat');
    }
    setSearchParams(next, { replace: true });
  };

  const updateExpertKind = (kind: ExpertMarketKind) => {
    const next = new URLSearchParams(searchParams);
    if (kind === 'all') {
      next.delete('kind');
    } else {
      next.set('kind', kind);
    }
    setSearchParams(next, { replace: true });
  };

  const updateExpertSort = (sort: 'popular' | 'newest') => {
    const next = new URLSearchParams(searchParams);
    if (sort === 'newest') {
      next.set('sort', 'newest');
    } else {
      next.delete('sort');
    }
    setSearchParams(next, { replace: true });
  };

  const openMarket = (tab: import('../../types/market').MarketTab = 'experts', kind?: ExpertMarketKind) => {
    navigate(
      marketPath(tab, expertMarketQuery, {
        kind: kind ?? expertKind,
        connectorId: searchParams.get('connector') ?? undefined,
      }),
    );
  };

  return {
    location,
    navigate,
    searchParams,
    routeSessionId,
    shareToken,
    isShareReplay,
    isNewTask,
    isMyFiles,
    isAssistantHub,
    isProjectsHub,
    isAutomationHub,
    isMoreHub,
    isNavShell,
    isAuditLog,
    isDevStudio,
    isSettings,
    settingsSection,
    isMarket,
    marketTab,
    expertMarketQuery,
    expertCategory,
    expertKind,
    expertSort,
    updateExpertQuery,
    updateExpertCategory,
    updateExpertKind,
    updateExpertSort,
    openMarket,
  };
}
