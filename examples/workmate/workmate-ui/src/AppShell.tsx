import { useCallback, useEffect, useMemo, useReducer, useRef, useState, type CSSProperties } from 'react';
import {
  autoArchiveSessions,
  confirmPlan,
  createSession,
  getMemory,
  getSessionLimits,
  listExperts,
  listModels,
  listSessionRunEvents,
  listSessionSummaries,
  listWorkspaces,
  patchSessionConnectors,
  patchSessionMetadata,
  patchSessionSkills,
  postExpertTransition,
  rememberSession,
  updatePlan,
} from './api/client';
import { fetchWelcomeConfig } from './api/welcome';
import { listConnectors, listSkills } from './api/market';
import { checkApiHealth } from './api/sse';
import { getCloudSessionByLinked, type CloudSession } from './api/cloud';
import { getTenantQuota, type TenantQuota } from './api/tenant';
import { uploadSessionAttachments } from './api/attachments';
import { getUserProfile, saveUserProfile } from './api/profile';
import { useChatStream } from './hooks/useChatStream';
import { useAcpSidecarImport } from './hooks/useAcpSidecarImport';
import { useSettings } from './features/settings/SettingsProvider';
import { ApiBanner } from './components/ApiBanner';
import { TenantQuotaBanner } from './components/TenantQuotaBanner';
import { IdleCapabilityReminder } from './components/IdleCapabilityReminder';
import { WorkbenchLayout } from './layouts/WorkbenchLayout';
import { OnboardingFlow, ONBOARDING_DONE_KEY, saveOnboardingProfile } from './views/onboarding/OnboardingFlow';
import { appShellReducer, createInitialAppState } from './features/shell/appShellState';
import { useAppShellRoutes } from './features/shell/useAppShellRoutes';
import { usePendingHitlSync } from './features/shell/usePendingHitlSync';
import { useDetailPanelState } from './features/shell/useDetailPanelState';
import {
  usePendingInitialMessage,
  useSessionChatCleanup,
  useSessionChatSync,
  useSessionRouteEffects,
} from './features/shell/useSessionLifecycle';
import { useTeamSessionSync } from './features/shell/useTeamSessionSync';
import { useHitlActions } from './features/shell/useHitlActions';
import { buildInputDockConfig } from './features/shell/buildInputDockConfig';
import { AppShellMainContent } from './features/shell/AppShellMainContent';
import { AppShellOverlays } from './features/shell/AppShellOverlays';
import { lastUserSeq } from './lib/chatItems';
import { isBusinessApprovalTool, sessionHasPendingApproval } from './lib/businessApproval';
import { sessionHasPendingQuestion } from './lib/questionCard';
import { initialTeamState } from './lib/teamStatus';
import { WELCOME_FALLBACK } from './lib/welcomeFallback';
import { isDefaultSessionWorkspaceKey } from './lib/sessionWorkspace';
import { formatDateTime } from './lib/formatLocale';
import { formatQuotaExceededToast, isQuotaOrSessionLimitError } from './lib/quotaAlert';
import { countActiveSessions, isAtSessionLimit, isAutoArchiveEnabled } from './lib/sessionLimits';
import { formatAutoArchiveNotice } from './lib/autoArchiveNotice';
import {
  autoConnectExpertConnectors,
  expertRecommendedConnectorIds,
  markExpertConnectorsRecent,
} from './lib/expertConnectorBootstrap';
import { normalizeConnectorIds } from './lib/connectorId';
import { invalidateSessionChatHydration, loadSessionChatItems } from './lib/sessionChatLoad';
import { saveDetailPanelVisible } from './lib/detailPanelPrefs';
import {
  AUDIT_LOG_PATH,
  AUTOMATION_PATH,
  MY_FILES_PATH,
  NEW_TASK_PATH,
  sessionPath,
  settingsPath,
} from './lib/paths';
import type { PlaybookCard, WelcomeConfig } from './types/welcome';
import type {
  Expert,
  MemoryStatus,
  ModelCatalog,
  ModelEffort,
  PermissionMode,
  Session,
  SessionLimits,
} from './types/api';
import type { ConnectorInfo, SkillInfo } from './types/market';
import type { WorkspacePreset } from './types/workspace';
import type { ApprovalRequiredPayload } from './types/events';
import type { GitSelection } from './components/GitTaskStarterPanel';
import type { PlanStep } from './types/events';

export function AppShell() {
  const routes = useAppShellRoutes();
  const {
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
  } = routes;

  const [state, dispatch] = useReducer(appShellReducer, undefined, createInitialAppState);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [apiOnline, setApiOnline] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [pendingBySession, setPendingBySession] = useState<Record<string, ApprovalRequiredPayload>>({});
  const [artifactRefreshKey, setArtifactRefreshKey] = useState(0);
  const detailPanel = useDetailPanelState();
  const [experts, setExperts] = useState<Expert[]>([]);
  const [expertsLoaded, setExpertsLoaded] = useState(false);
  const [marketSkills, setMarketSkills] = useState<SkillInfo[]>([]);
  const [marketConnectors, setMarketConnectors] = useState<ConnectorInfo[]>([]);
  const [workspacePresets, setWorkspacePresets] = useState<WorkspacePreset[]>([]);
  const [selectedWorkspacePath, setSelectedWorkspacePath] = useState('');
  const [gitSelection, setGitSelection] = useState<GitSelection | null>(null);
  const [selectedExpertId, setSelectedExpertId] = useState('');
  const [selectedPermissionMode, setSelectedPermissionMode] = useState<PermissionMode>('CRAFT');
  const [modelCatalog, setModelCatalog] = useState<ModelCatalog | null>(null);
  const [selectedModelId, setSelectedModelId] = useState('');
  const [linkedCloudSession, setLinkedCloudSession] = useState<CloudSession | null>(null);
  const [cloudDrawerOpen, setCloudDrawerOpen] = useState(false);
  const [changeExpertOpen, setChangeExpertOpen] = useState(false);
  const [changeExpertBusy, setChangeExpertBusy] = useState(false);
  const [tenantQuota, setTenantQuota] = useState<TenantQuota | null>(null);
  const [selectedEffort, setSelectedEffort] = useState<ModelEffort>('AUTO');
  const [newTaskDraft, setNewTaskDraft] = useState('');
  const [draftEnabledConnectorIds, setDraftEnabledConnectorIds] = useState<string[]>([]);
  const [draftEnabledSkillIds, setDraftEnabledSkillIds] = useState<string[]>([]);
  const [detailExpert, setDetailExpert] = useState<Expert | null>(null);
  const [summonExpert, setSummonExpert] = useState<Expert | null>(null);
  const [summonBusy, setSummonBusy] = useState(false);
  const [sessionDraftSeed, setSessionDraftSeed] = useState('');
  const [welcomeConfig, setWelcomeConfig] = useState<WelcomeConfig>(WELCOME_FALLBACK);
  const [welcomeHydrated, setWelcomeHydrated] = useState(false);
  const [confirmingPlan, setConfirmingPlan] = useState(false);
  const [savingPlan, setSavingPlan] = useState(false);
  const [creatingSession, setCreatingSession] = useState(false);
  const [sessionLimits, setSessionLimits] = useState<SessionLimits | null>(null);
  const [maxSessionsDialog, setMaxSessionsDialog] = useState<SessionLimits | null>(null);
  const [archiveToast, setArchiveToast] = useState<string | null>(null);
  const [rememberSessionBusy, setRememberSessionBusy] = useState(false);
  const [memoryStatus, setMemoryStatus] = useState<MemoryStatus | null>(null);
  const [showOnboarding, setShowOnboarding] = useState(false);
  const [shareDialogOpen, setShareDialogOpen] = useState(false);
  const [clearQueueBusy, setClearQueueBusy] = useState(false);
  const [shareToast, setShareToast] = useState<string | null>(null);
  const { settings } = useSettings();

  const pendingInitialMessageRef = useRef<string | null>(null);
  const sessionsLoadedRef = useRef(false);
  const serverChatLoadedRef = useRef<Set<string>>(new Set());
  const teamEventsLoadedRef = useRef<Set<string>>(new Set());
  const teamEventsCacheRef = useRef<Record<string, Awaited<ReturnType<typeof listSessionRunEvents>>>>({});
  const teamPollSeqRef = useRef<Record<string, number>>({});

  const { syncPendingQuestions, reconcilePendingApprovals } = usePendingHitlSync(
    dispatch,
    setPendingBySession,
  );

  const viewSessionId =
    routeSessionId && !isShareReplay && !isNewTask ? routeSessionId : state.activeId;

  const isOpenSessionView = Boolean(routeSessionId && !isShareReplay && !isNewTask);

  const refreshSessions = useCallback(async () => {
    const [online, data] = await Promise.all([
      checkApiHealth(),
      Promise.all([listSessionSummaries(), getSessionLimits()]),
    ]);
    setApiOnline(online);
    if (!online) {
      throw new Error('无法连接 workmate-api（请确认 :8080 已启动）');
    }
    const [sessions, limits] = data;
    dispatch({ type: 'set-sessions', sessions });
    setSessionLimits(limits);
    return sessions;
  }, []);

  useEffect(() => {
    refreshSessions()
      .catch((err) => setLoadError((err as Error).message))
      .finally(() => {
        sessionsLoadedRef.current = true;
        setLoadingSessions(false);
      });
  }, [refreshSessions]);

  const autoArchiveEnabled = isAutoArchiveEnabled(settings.autoArchiveOnCreate, sessionLimits);

  useEffect(() => {
    if (!archiveToast) {
      return;
    }
    const timer = window.setTimeout(() => setArchiveToast(null), 4000);
    return () => window.clearTimeout(timer);
  }, [archiveToast]);

  const refreshMemory = useCallback(async () => {
    try {
      const online = await checkApiHealth();
      if (!online) {
        setMemoryStatus(null);
        return;
      }
      setMemoryStatus(await getMemory());
    } catch {
      setMemoryStatus(null);
    }
  }, []);

  const handleRememberSession = useCallback(async () => {
    if (!state.activeId) {
      return;
    }
    setRememberSessionBusy(true);
    try {
      const result = await rememberSession(state.activeId);
      await refreshMemory();
      if (result.status === 'captured' && result.entries.length > 0) {
        setArchiveToast(`已记住 ${result.entries.length} 条长期记忆`);
      } else if (result.status === 'no-op') {
        setArchiveToast('未发现新的可记忆内容');
      } else if (result.reason) {
        setArchiveToast(result.reason);
      }
    } catch (err) {
      setLoadError((err as Error).message);
    } finally {
      setRememberSessionBusy(false);
    }
  }, [refreshMemory, state.activeId]);

  useEffect(() => {
    void refreshMemory();
  }, [refreshMemory]);

  const bumpArtifacts = useCallback(() => {
    setArtifactRefreshKey((key) => key + 1);
  }, []);

  const handleSessionMetadataChange = useCallback(
    async (
      sessionId: string,
      patch: {
        pinned?: boolean;
        archived?: boolean;
        modelId?: string | null;
        effort?: ModelEffort | null;
        enabledConnectorIds?: string[];
        enabledSkillIds?: string[];
        permissionMode?: PermissionMode;
        expertId?: string | null;
      },
    ) => {
      try {
        const updated = await patchSessionMetadata(sessionId, patch);
        dispatch({ type: 'upsert-session', session: updated });
        await refreshSessions();
      } catch (err) {
        setLoadError((err as Error).message);
      }
    },
    [refreshSessions],
  );

  const handleModelChange = useCallback(
    (modelId: string) => {
      if (state.activeId && !isNewTask) {
        void handleSessionMetadataChange(state.activeId, { modelId });
        return;
      }
      setSelectedModelId(modelId);
    },
    [handleSessionMetadataChange, isNewTask, state.activeId],
  );

  const handleEffortChange = useCallback(
    (effort: ModelEffort) => {
      if (state.activeId && !isNewTask) {
        void handleSessionMetadataChange(state.activeId, { effort });
        return;
      }
      setSelectedEffort(effort);
    },
    [handleSessionMetadataChange, isNewTask, state.activeId],
  );

  const handlePermissionModeChange = useCallback(
    (mode: PermissionMode) => {
      const session = state.sessions.find((item) => item.id === state.activeId);
      if (session?.archivedAt) {
        return;
      }
      if (state.activeId && !isNewTask) {
        void handleSessionMetadataChange(state.activeId, { permissionMode: mode });
        return;
      }
      setSelectedPermissionMode(mode);
    },
    [handleSessionMetadataChange, isNewTask, state.activeId, state.sessions],
  );

  const handleEnabledConnectorIdsChange = useCallback(
    (connectorIds: string[]) => {
      if (state.activeId && !isNewTask) {
        void (async () => {
          try {
            const updated = await patchSessionConnectors(state.activeId!, connectorIds);
            dispatch({ type: 'upsert-session', session: updated });
            void refreshSessions().catch(() => undefined);
          } catch (err) {
            setLoadError((err as Error).message);
          }
        })();
        return;
      }
      setDraftEnabledConnectorIds(connectorIds);
    },
    [isNewTask, refreshSessions, state.activeId],
  );

  const handleEnabledSkillIdsChange = useCallback(
    (skillIds: string[]) => {
      if (state.activeId && !isNewTask) {
        void (async () => {
          try {
            const updated = await patchSessionSkills(state.activeId!, skillIds);
            dispatch({ type: 'upsert-session', session: updated });
            void refreshSessions().catch(() => undefined);
          } catch (err) {
            setLoadError((err as Error).message);
          }
        })();
        return;
      }
      setDraftEnabledSkillIds(skillIds);
    },
    [isNewTask, refreshSessions, state.activeId],
  );

  const {
    runPrompt,
    editMessage,
    retryMessage,
    stopPrompt,
    clearQueue,
    isStreaming,
    streamingBySession,
    streamStageBySession,
    sessionErrorBySession,
    queueDepthBySession,
    teamStateBySession,
    hydrateTeamFromEvents,
    resumeTeamRunIfActive,
    resumeRunAfterHitl,
    applyTeamSnapshot,
    syncChatFromServer,
    refreshMemberSurfaceFromEvents,
    clearSessionError,
  } = useChatStream({
    dispatch,
    refreshSessions,
    bumpArtifacts,
    onApprovalRequired: (sessionId, payload) => {
      if (isBusinessApprovalTool(payload.tool)) {
        return;
      }
      setPendingBySession((prev) => ({ ...prev, [sessionId]: payload }));
    },
    onArtifactAdded: (path) => {
      detailPanel.setArtifactAutoOpenPath(path);
      detailPanel.setArtifactAutoOpenMode('preview');
    },
    onWriteCompleted: (_sessionId, path) => {
      detailPanel.setDetailPanelVisible(true);
      saveDetailPanelVisible(true);
      detailPanel.setArtifactAutoOpenPath(path);
      detailPanel.setArtifactAutoOpenMode('changes');
    },
    onRunCompleted: (sessionId) => {
      void refreshMemory();
      setPendingBySession((prev) => {
        if (!prev[sessionId]) {
          return prev;
        }
        const next = { ...prev };
        delete next[sessionId];
        return next;
      });
    },
    resolveExpert: (sessionId) => {
      const session = state.sessions.find((item) => item.id === sessionId);
      if (!session?.expertId) {
        return null;
      }
      return experts.find((expert) => expert.id === session.expertId) ?? null;
    },
  });

  const hydrateTeamFromEventsRef = useRef(hydrateTeamFromEvents);
  hydrateTeamFromEventsRef.current = hydrateTeamFromEvents;

  const sidecarImport = useAcpSidecarImport({
    sessionId: state.activeId,
    onImported: async (sessionId, result) => {
      serverChatLoadedRef.current.delete(sessionId);
      teamEventsLoadedRef.current.delete(sessionId);
      delete teamEventsCacheRef.current[sessionId];
      invalidateSessionChatHydration(sessionId);
      try {
        const [events, serverItems] = await Promise.all([
          listSessionRunEvents(sessionId),
          loadSessionChatItems(sessionId),
        ]);
        if (events.length > 0) {
          hydrateTeamFromEventsRef.current(sessionId, events);
        }
        dispatch({ type: 'set-chat-from-server', sessionId, items: serverItems });
        void reconcilePendingApprovals(sessionId, { events, chatItems: serverItems });
        void syncPendingQuestions(sessionId);
      } catch {
        // import succeeded; refresh is best-effort
      }
      setShareToast(`已导入 ${result.ingested} 条 ACP 事件`);
    },
    onError: (message) => setShareToast(message),
  });

  useEffect(() => {
    void (async () => {
      try {
        const items = await listExperts();
        setExperts(items);
      } catch {
        // experts optional when API old version
      } finally {
        setExpertsLoaded(true);
      }
    })();
    void listSkills().then(setMarketSkills).catch(() => undefined);
    void listConnectors().then(setMarketConnectors).catch(() => undefined);
  }, []);

  useEffect(() => {
    void listModels()
      .then((catalog) => {
        setModelCatalog(catalog);
        setSelectedModelId((current) => current || catalog.defaultModelId);
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    void fetchWelcomeConfig()
      .then(setWelcomeConfig)
      .catch(() => setWelcomeConfig(WELCOME_FALLBACK))
      .finally(() => setWelcomeHydrated(true));
  }, []);

  useEffect(() => {
    if (!welcomeHydrated || !isNewTask) {
      return;
    }
    const onboarding = welcomeConfig.onboarding;
    if (onboarding?.enabled && !localStorage.getItem(ONBOARDING_DONE_KEY)) {
      setShowOnboarding(true);
    }
  }, [welcomeHydrated, isNewTask, welcomeConfig.onboarding]);

  useEffect(() => {
    if (!welcomeHydrated) {
      return;
    }
    void getUserProfile()
      .then((profile) => {
        if (profile.role || profile.interests?.length) {
          saveOnboardingProfile({ role: profile.role ?? '', interests: profile.interests ?? [] });
        }
      })
      .catch(() => undefined);
  }, [welcomeHydrated]);

  useEffect(() => {
    void listWorkspaces()
      .then((presets) => {
        setWorkspacePresets(presets);
        if (presets.length > 0 && !selectedWorkspacePath) {
          const defaultPreset = presets.find((preset) => preset.path === '') ?? presets[0];
          setSelectedWorkspacePath(defaultPreset.path);
        }
      })
      .catch(() => undefined);
  }, []);

  useSessionRouteEffects({
    loadingSessions,
    sessionsLoadedRef,
    routeSessionId,
    isShareReplay,
    isNewTask,
    pathname: location.pathname,
    locationState: location.state,
    locationSearch: location.search,
    searchParams,
    activeId: state.activeId,
    navigate,
    dispatch,
    setSelectedExpertId,
    setNewTaskDraft,
    pendingInitialMessageRef,
    setArtifactAutoOpenPath: detailPanel.setArtifactAutoOpenPath,
  });

  useSessionChatSync({
    isOpenSessionView,
    routeSessionId,
    streamingBySession,
    chatBySession: state.chatBySession,
    dispatch,
    serverChatLoadedRef,
    teamEventsCacheRef,
    reconcilePendingApprovals,
    syncPendingQuestions,
  });

  useSessionChatCleanup(viewSessionId, serverChatLoadedRef, teamEventsLoadedRef);

  const { teamHistoryLoadingBySession } = useTeamSessionSync({
    isOpenSessionView,
    routeSessionId,
    sessions: state.sessions,
    experts,
    streamingBySession,
    hydrateTeamFromEvents,
    applyTeamSnapshot,
    resumeTeamRunIfActive,
    refreshMemberSurfaceFromEvents,
    syncChatFromServer,
    teamEventsLoadedRef,
    teamEventsCacheRef,
    teamPollSeqRef,
    serverChatLoadedRef,
  });

  useEffect(() => {
    if (!isOpenSessionView || !routeSessionId) {
      setLinkedCloudSession(null);
      setCloudDrawerOpen(false);
      return undefined;
    }
    const sessionId = routeSessionId;
    let cancelled = false;
    void (async () => {
      try {
        const cloud = await getCloudSessionByLinked(sessionId);
        if (!cancelled) {
          setLinkedCloudSession(cloud);
        }
      } catch {
        if (!cancelled) {
          setLinkedCloudSession(null);
          setCloudDrawerOpen(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isOpenSessionView, routeSessionId]);

  const refreshTenantQuota = useCallback(async () => {
    try {
      setTenantQuota(await getTenantQuota());
    } catch {
      setTenantQuota(null);
    }
  }, []);

  useEffect(() => {
    void refreshTenantQuota();
  }, [refreshTenantQuota, state.sessions.length]);

  const activeSession =
    viewSessionId != null
      ? state.sessions.find((session) => session.id === viewSessionId) ?? null
      : null;
  const chatItems = viewSessionId ? (state.chatBySession[viewSessionId] ?? []) : [];
  const activeStreaming = isStreaming(viewSessionId);
  const activeStreamStage = viewSessionId ? (streamStageBySession[viewSessionId] ?? null) : null;
  const activeTeamHistoryLoading = viewSessionId
    ? Boolean(teamHistoryLoadingBySession[viewSessionId])
    : false;
  const activeExpert = activeSession?.expertId
    ? experts.find((e) => e.id === activeSession.expertId)
    : null;
  const liveTeam = viewSessionId ? teamStateBySession[viewSessionId] : undefined;
  const activeTeam = useMemo(() => {
    const seeded = activeExpert?.expertType === 'team' ? initialTeamState(activeExpert) : null;
    if (!liveTeam) {
      return seeded;
    }
    if (!seeded) {
      return liveTeam;
    }
    return {
      ...seeded,
      ...liveTeam,
      teamRuntime: liveTeam.teamRuntime ?? seeded.teamRuntime,
      visualizationMode: liveTeam.visualizationMode ?? seeded.visualizationMode,
      members: liveTeam.members.length > 0 ? liveTeam.members : seeded.members,
    };
  }, [activeExpert, liveTeam]);

  const activeRunError = viewSessionId ? sessionErrorBySession[viewSessionId] ?? null : null;
  const activeRetryFromSeq = lastUserSeq(chatItems);
  const activeQueueDepth = viewSessionId ? queueDepthBySession[viewSessionId] ?? 0 : 0;
  const activePending = viewSessionId ? (pendingBySession[viewSessionId] ?? null) : null;
  const modalPending =
    activePending && !isBusinessApprovalTool(activePending.tool) ? activePending : null;
  const sessionsWithPendingApproval = useMemo(() => {
    const ids = new Set<string>();
    for (const session of state.sessions) {
      if (sessionHasPendingApproval(session.id, state.chatBySession, pendingBySession)) {
        ids.add(session.id);
      }
    }
    return ids;
  }, [pendingBySession, state.chatBySession, state.sessions]);

  const canRememberSession =
    Boolean(memoryStatus?.enabled) &&
    chatItems.some((item) => item.kind === 'user') &&
    !activeStreaming;

  const handleCreate = useCallback(
    async (options?: {
      title?: string;
      expertId?: string;
      permissionMode?: PermissionMode;
      initialMessage?: string;
      workspacePath?: string;
      gitBranch?: string;
      enabledConnectorIds?: string[];
      enabledSkillIds?: string[];
    }) => {
      const activeCount = sessionLimits?.activeCount ?? countActiveSessions(state.sessions);
      const maxActive = sessionLimits?.maxActive ?? 50;
      if (isAtSessionLimit(activeCount, maxActive) && !autoArchiveEnabled) {
        setArchiveToast(formatQuotaExceededToast(
          tenantQuota,
          `已达活跃任务上限（${activeCount}/${maxActive}）`,
        ));
        setMaxSessionsDialog({ activeCount, maxActive });
        return null;
      }
      try {
        setLoadError(null);
        setCreatingSession(true);
        const expertId = options?.expertId ?? selectedExpertId;
        const mode = options?.permissionMode ?? selectedPermissionMode;
        const workspacePath = options?.workspacePath ?? selectedWorkspacePath;
        const gitBranch = options?.gitBranch ?? gitSelection?.branch;
        const expert = expertId ? experts.find((item) => item.id === expertId) : undefined;
        const title =
          options?.title ??
          (expert
            ? `${expert.name} ${formatDateTime(new Date())}`
            : `任务 ${formatDateTime(new Date())}`);
        const connectorIdsToEnable = normalizeConnectorIds([
          ...draftEnabledConnectorIds,
          ...(options?.enabledConnectorIds ?? []),
        ]);
        const skillIdsToEnable = [
          ...new Set([
            ...draftEnabledSkillIds,
            ...(options?.enabledSkillIds ?? []),
          ].map((id) => id.trim()).filter(Boolean)),
        ];
        const created = await createSession({
          title,
          expertId: expertId || undefined,
          permissionMode: mode,
          workspacePath: workspacePath || undefined,
          gitBranch: gitBranch || undefined,
          modelId: selectedModelId || modelCatalog?.defaultModelId || undefined,
          effort: selectedEffort !== 'AUTO' ? selectedEffort : undefined,
          autoArchive: autoArchiveEnabled,
          enabledConnectorIds: connectorIdsToEnable.length > 0 ? connectorIdsToEnable : undefined,
          enabledSkillIds: skillIdsToEnable.length > 0 ? skillIdsToEnable : undefined,
        });
        const session = created;
        if (connectorIdsToEnable.length > 0) {
          setDraftEnabledConnectorIds([]);
        }
        if (skillIdsToEnable.length > 0) {
          setDraftEnabledSkillIds([]);
        }
        if (created.autoArchived?.length) {
          setArchiveToast(formatAutoArchiveNotice(created.autoArchived));
        }
        if (expertId) {
          setSelectedExpertId(expertId);
        }
        dispatch({ type: 'upsert-session', session });
        dispatch({ type: 'select', id: session.id });
        if (options?.initialMessage) {
          pendingInitialMessageRef.current = options.initialMessage;
        }
        navigate(sessionPath(session.id), { replace: true });
        void refreshSessions().catch(() => undefined);
        return session.id;
      } catch (err) {
        const message = (err as Error).message;
        if (
          message.includes('Session limit exceeded')
          || message.includes('maxActive')
          || message.includes('Not enough archivable')
          || message.includes('Insufficient archivable')
        ) {
          setArchiveToast(formatQuotaExceededToast(tenantQuota, message));
          try {
            const limits = await getSessionLimits();
            setSessionLimits(limits);
            setMaxSessionsDialog(limits);
          } catch {
            setMaxSessionsDialog({ activeCount, maxActive });
          }
        } else if (isQuotaOrSessionLimitError(message)) {
          setArchiveToast(formatQuotaExceededToast(tenantQuota, message));
          setLoadError(message);
        } else {
          setLoadError(message);
        }
        return null;
      } finally {
        setCreatingSession(false);
      }
    },
    [
      autoArchiveEnabled,
      draftEnabledConnectorIds,
      draftEnabledSkillIds,
      experts,
      gitSelection?.branch,
      modelCatalog?.defaultModelId,
      navigate,
      refreshSessions,
      selectedEffort,
      selectedExpertId,
      selectedModelId,
      selectedPermissionMode,
      selectedWorkspacePath,
      sessionLimits,
      state.sessions,
      tenantQuota,
    ],
  );

  usePendingInitialMessage(
    pendingInitialMessageRef,
    state.activeId,
    routeSessionId,
    runPrompt,
  );

  const handleSend = async (
    message: string,
    extras?: import('./types/sendMessage').SendMessageExtras,
  ) => {
    if (isNewTask) {
      setNewTaskDraft('');
    } else {
      setSessionDraftSeed('');
    }
    let sessionId = state.activeId;
    if (!sessionId) {
      sessionId = await handleCreate();
      if (!sessionId) {
        return;
      }
    }
    let attachments = extras?.attachments ?? [];
    if (extras?.files?.length) {
      attachments = [...attachments, ...(await uploadSessionAttachments(sessionId, extras.files))];
    }
    await runPrompt(
      sessionId,
      message,
      extras?.mentions,
      attachments.length > 0 ? attachments : undefined,
    );
  };

  const { approvalBusy, questionBusy, handleApprovalDecide, handleQuestionAnswer } = useHitlActions(
    state,
    dispatch,
    pendingBySession,
    setPendingBySession,
    resumeRunAfterHitl,
    setLoadError,
  );

  const handleSelectSession = (id: string) => {
    setSessionDraftSeed('');
    navigate(sessionPath(id));
  };

  const handleNewTask = () => {
    const activeCount = sessionLimits?.activeCount ?? countActiveSessions(state.sessions);
    const maxActive = sessionLimits?.maxActive ?? 50;
    if (isAtSessionLimit(activeCount, maxActive) && !autoArchiveEnabled) {
      setArchiveToast(formatQuotaExceededToast(
        tenantQuota,
        `已达活跃任务上限（${activeCount}/${maxActive}）`,
      ));
      setMaxSessionsDialog({ activeCount, maxActive });
      return;
    }
    setSessionDraftSeed('');
    navigate(NEW_TASK_PATH);
  };

  const handleSessionLimitHelp = useCallback(() => {
    const activeCount = sessionLimits?.activeCount ?? countActiveSessions(state.sessions);
    const maxActive = sessionLimits?.maxActive ?? 50;
    setMaxSessionsDialog({ activeCount, maxActive, ...(sessionLimits ?? {}) });
  }, [sessionLimits, state.sessions]);

  const handleArchiveFromLimitDialog = useCallback(
    async (sessionId: string) => {
      await handleSessionMetadataChange(sessionId, { archived: true });
      try {
        const limits = await getSessionLimits();
        setSessionLimits(limits);
        if (!isAtSessionLimit(limits.activeCount, limits.maxActive)) {
          setMaxSessionsDialog(null);
        } else {
          setMaxSessionsDialog(limits);
        }
      } catch {
        const activeCount = countActiveSessions(state.sessions);
        const maxActive = sessionLimits?.maxActive ?? 50;
        setMaxSessionsDialog({ activeCount, maxActive });
      }
    },
    [handleSessionMetadataChange, sessionLimits, state.sessions],
  );

  const handleAutoArchiveBulk = useCallback(
    async (count: number) => {
      const result = await autoArchiveSessions(count);
      await refreshSessions();
      const limits = await getSessionLimits();
      setSessionLimits(limits);
      if (result.archived.length > 0) {
        setArchiveToast(formatAutoArchiveNotice(result.archived));
      }
      if (!isAtSessionLimit(limits.activeCount, limits.maxActive)) {
        setMaxSessionsDialog(null);
      } else {
        setMaxSessionsDialog(limits);
      }
    },
    [refreshSessions],
  );

  const handlePlanConfirm = useCallback(
    async (planId: string) => {
      const sessionId = state.activeId;
      if (!sessionId) {
        return;
      }
      setConfirmingPlan(true);
      try {
        await confirmPlan(sessionId);
        dispatch({ type: 'confirm-plan', sessionId, planId });
        await refreshSessions();
        void resumeRunAfterHitl(sessionId);
      } catch (err) {
        setLoadError((err as Error).message);
      } finally {
        setConfirmingPlan(false);
      }
    },
    [dispatch, refreshSessions, resumeRunAfterHitl, state.activeId],
  );

  const handlePlanUpdate = useCallback(
    async (planId: string, steps: PlanStep[], title?: string) => {
      const sessionId = state.activeId;
      if (!sessionId) {
        return;
      }
      setSavingPlan(true);
      try {
        await updatePlan(sessionId, planId, {
          title,
          steps: steps.map((step) => ({
            id: step.id,
            title: step.title,
            status: step.status,
          })),
        });
        dispatch({
          type: 'upsert-plan',
          sessionId,
          plan: { planId, title, steps },
        });
      } catch (err) {
        setLoadError((err as Error).message);
      } finally {
        setSavingPlan(false);
      }
    },
    [dispatch, state.activeId],
  );

  const handleInspirationLaunch = useCallback(
    async (playbook: PlaybookCard, autoSend = true) => {
      if (playbook.expertId) {
        setSelectedExpertId(playbook.expertId);
      }
      if (autoSend) {
        await handleCreate({
          title: playbook.title,
          expertId: playbook.expertId,
          initialMessage: playbook.initPrompt,
        });
      } else {
        setNewTaskDraft(playbook.initPrompt);
        navigate(NEW_TASK_PATH);
      }
    },
    [handleCreate, navigate],
  );

  const handleDiscoverLaunch = useCallback(
    async (payload: { initPrompt: string; expertId?: string; title: string }) => {
      if (payload.expertId) {
        setSelectedExpertId(payload.expertId);
      }
      await handleCreate({
        title: payload.title,
        expertId: payload.expertId,
        initialMessage: payload.initPrompt,
      });
    },
    [handleCreate],
  );

  const handleOnboardingComplete = useCallback(
    async (payload: {
      role: string;
      interests: string[];
      sampleTask: { title: string; prompt: string; expertId?: string };
    }) => {
      localStorage.setItem(ONBOARDING_DONE_KEY, '1');
      setShowOnboarding(false);
      saveOnboardingProfile({ role: payload.role, interests: payload.interests });
      void saveUserProfile({ role: payload.role, interests: payload.interests }).catch(() => undefined);
      if (payload.sampleTask.expertId) {
        setSelectedExpertId(payload.sampleTask.expertId);
      }
      await handleCreate({
        title: payload.sampleTask.title,
        expertId: payload.sampleTask.expertId,
        initialMessage: payload.sampleTask.prompt,
      });
    },
    [handleCreate],
  );

  const resolveSessionWorkspacePath = useCallback(
    (session: Session) => {
      const key = session.workspaceKey ?? '';
      if (key && !isDefaultSessionWorkspaceKey(key)) {
        return key;
      }
      return selectedWorkspacePath;
    },
    [selectedWorkspacePath],
  );

  const handleChangeExpertConfirm = useCallback(
    async (expertId: string, archiveCurrent: boolean) => {
      const session = state.sessions.find((item) => item.id === state.activeId);
      if (!state.activeId || !session) {
        return;
      }
      setChangeExpertBusy(true);
      try {
        if (archiveCurrent) {
          await handleSessionMetadataChange(state.activeId, { archived: true });
        }
        const expert = experts.find((item) => item.id === expertId);
        await handleCreate({
          expertId,
          workspacePath: resolveSessionWorkspacePath(session),
          title: expert ? `${expert.name} · ${session.title}` : session.title,
        });
        setChangeExpertOpen(false);
      } finally {
        setChangeExpertBusy(false);
      }
    },
    [
      experts,
      handleCreate,
      handleSessionMetadataChange,
      resolveSessionWorkspacePath,
      state.activeId,
      state.sessions,
    ],
  );

  const handleSummonConfirm = async () => {
    if (!summonExpert) {
      return;
    }
    setSummonBusy(true);
    try {
      const draftPrompt = summonExpert.defaultInitPrompt?.trim() ?? '';
      const expertConnectorIds = expertRecommendedConnectorIds(summonExpert);
      void autoConnectExpertConnectors(expertConnectorIds)
        .then((connectors) => {
          if (expertConnectorIds.length === 0) {
            return;
          }
          markExpertConnectorsRecent(expertConnectorIds);
          if (connectors) {
            setMarketConnectors(connectors);
          }
        })
        .catch(() => undefined);

      const continueInCurrentSession =
        isOpenSessionView && Boolean(state.activeId) && !activeSession?.archivedAt;

      if (continueInCurrentSession && state.activeId) {
        const mergedConnectorIds = normalizeConnectorIds([
          ...(activeSession?.enabledConnectorIds ?? []),
          ...expertConnectorIds,
        ]);
        const updated = await postExpertTransition(state.activeId, {
          expertId: summonExpert.id,
          ...(mergedConnectorIds.length > 0 ? { enabledConnectorIds: mergedConnectorIds } : {}),
        });
        dispatch({ type: 'upsert-session', session: updated });
        serverChatLoadedRef.current.delete(state.activeId);
        invalidateSessionChatHydration(state.activeId);
        void loadSessionChatItems(state.activeId, { fresh: true })
          .then((items) => {
            dispatch({ type: 'set-chat-from-server', sessionId: state.activeId!, items });
          })
          .catch(() => undefined);
        void refreshSessions().catch(() => undefined);
        if (draftPrompt) {
          setSessionDraftSeed(draftPrompt);
        }
      } else {
        const sessionId = await handleCreate({
          expertId: summonExpert.id,
          enabledConnectorIds: expertConnectorIds,
        });
        if (sessionId && draftPrompt) {
          setSessionDraftSeed(draftPrompt);
        }
      }
      setSummonExpert(null);
      setDetailExpert(null);
    } catch (error) {
      setLoadError((error as Error).message);
    } finally {
      setSummonBusy(false);
    }
  };

  useEffect(() => {
    if (!shareToast) {
      return;
    }
    const timer = window.setTimeout(() => setShareToast(null), 2500);
    return () => window.clearTimeout(timer);
  }, [shareToast]);

  const showArtifactPanel = isOpenSessionView
    && !isMarket
    && !isAuditLog
    && !isSettings
    && !isMyFiles
    && !isNavShell;

  const detailPanelEnabled = showArtifactPanel && detailPanel.detailPanelSupported;
  const detailPanelRendered = detailPanelEnabled && detailPanel.detailPanelVisible;

  const sessionLocked = Boolean(viewSessionId) && !isNewTask;
  const dockExpertId = sessionLocked ? (activeSession?.expertId ?? '') : selectedExpertId;
  const dockPermissionMode = sessionLocked
    ? (activeSession?.permissionMode ?? 'CRAFT')
    : selectedPermissionMode;
  const dockModelId =
    activeSession?.modelId ?? selectedModelId ?? modelCatalog?.defaultModelId ?? '';
  const dockEffort = (activeSession?.effort ?? selectedEffort ?? 'AUTO') as ModelEffort;
  const hasPendingQuestion = state.activeId
    ? sessionHasPendingQuestion(state.activeId, state.chatBySession)
    : false;

  const inputDockConfig = buildInputDockConfig({
    experts,
    selectedExpertId: dockExpertId,
    permissionMode: dockPermissionMode,
    sessionLocked,
    activeStreaming,
    creatingSession,
    hasPendingQuestion,
    isNewTask,
    draftEnabledConnectorIds,
    draftEnabledSkillIds,
    activeSession,
    marketSkills,
    marketConnectors,
    workspacePresets,
    selectedWorkspacePath,
    gitSelection,
    activeId: state.activeId,
    modelCatalog,
    dockModelId,
    dockEffort,
    sessionDraftSeed,
    activeTeam,
    welcomePlaceholderNew:
      welcomeConfig?.dock.placeholderNew ??
      '今天帮你做些什么？ @ 引用对话文件，/ 调用技能与指令',
    welcomePlaceholderSession:
      welcomeConfig?.dock.placeholderSession ?? '输入任务描述，Enter 发送（Shift+Enter 换行）',
    onExpertChange: setSelectedExpertId,
    onPermissionModeChange: handlePermissionModeChange,
    onOpenMarket: (tab, kind) => openMarket(tab, kind),
    onExpertSummon: (expert) => {
      setSelectedExpertId(expert.id);
      setSummonExpert(expert);
    },
    onEnabledConnectorIdsChange: handleEnabledConnectorIdsChange,
    onEnabledSkillIdsChange: handleEnabledSkillIdsChange,
    onMarketSkillsChange: setMarketSkills,
    onMarketConnectorsChange: setMarketConnectors,
    onWorkspacePathChange: setSelectedWorkspacePath,
    onGitSelectionChange: setGitSelection,
    onModelChange: handleModelChange,
    onEffortChange: handleEffortChange,
    onSend: (message, extras) => void handleSend(message, extras),
    onStop: () => {
      if (state.activeId) {
        stopPrompt(state.activeId);
      }
    },
  });

  const assistantBrand = welcomeConfig.hero.title?.trim() || 'WorkMate';

  const workbenchVariant =
    isShareReplay
      ? 'share'
      : isAuditLog || isSettings || isMyFiles || isNavShell || isDevStudio
      ? 'audit'
      : isMarket
        ? 'market'
        : isNewTask
          ? 'new-task'
          : 'session';

  return (
    <div className="app-root">
      <ApiBanner
        online={apiOnline}
        error={loadError}
        onRetry={() => {
          setLoadError(null);
          setLoadingSessions(true);
          refreshSessions()
            .catch((err) => setLoadError((err as Error).message))
            .finally(() => setLoadingSessions(false));
        }}
      />
      <TenantQuotaBanner
        quota={tenantQuota}
        suppressActiveSessionAlerts={autoArchiveEnabled}
        onOpenQuota={() => navigate(settingsPath('quota'))}
      />
      {archiveToast && <p className="app-archive-toast" role="status">{archiveToast}</p>}
      <IdleCapabilityReminder />
      {showOnboarding && welcomeConfig.onboarding?.enabled && (
        <OnboardingFlow
          config={welcomeConfig.onboarding}
          onComplete={(payload) => void handleOnboardingComplete(payload)}
          onSkip={() => {
            localStorage.setItem(ONBOARDING_DONE_KEY, '1');
            setShowOnboarding(false);
          }}
        />
      )}
      <WorkbenchLayout
        variant={workbenchVariant}
        shellClassName={detailPanelRendered ? undefined : 'app-shell-detail-hidden'}
        shellStyle={
          detailPanelRendered
            ? ({ '--wm-detail-width': `${detailPanel.detailPanelWidth}px` } as CSSProperties)
            : undefined
        }
      >
        <AppShellMainContent
          isShareReplay={isShareReplay}
          shareToken={shareToken}
          sessions={state.sessions}
          experts={experts}
          expertsLoaded={expertsLoaded}
          workspacePresets={workspacePresets}
          activeId={viewSessionId}
          loadingSessions={loadingSessions}
          streamingBySession={streamingBySession}
          pendingBySession={pendingBySession}
          sessionsWithPendingApproval={sessionsWithPendingApproval}
          pathname={location.pathname}
          isSettings={isSettings}
          isAuditLog={isAuditLog}
          isDevStudio={isDevStudio}
          sessionLimits={sessionLimits}
          autoArchiveEnabled={autoArchiveEnabled}
          onSessionLimitHelp={handleSessionLimitHelp}
          onSessionMetadataChange={handleSessionMetadataChange}
          navigate={navigate}
          dispatch={dispatch}
          onSelectSession={handleSelectSession}
          onNewTask={handleNewTask}
          isAuditLogView={isAuditLog}
          isDevStudioView={isDevStudio}
          isMyFiles={isMyFiles}
          isAssistantHub={isAssistantHub}
          isProjectsHub={isProjectsHub}
          isAutomationHub={isAutomationHub}
          isMoreHub={isMoreHub}
          settingsSection={settingsSection}
          isMarket={isMarket}
          marketTab={marketTab}
          welcomeConfig={welcomeConfig}
          expertMarketQuery={expertMarketQuery}
          expertCategory={expertCategory}
          expertKind={expertKind}
          expertSort={expertSort}
          detailExpert={detailExpert}
          summonBusy={summonBusy}
          onExpertQueryChange={updateExpertQuery}
          onExpertCategoryChange={updateExpertCategory}
          onExpertKindChange={updateExpertKind}
          onExpertSortChange={updateExpertSort}
          onMarketTabChange={(tab) => openMarket(tab)}
          onMarketBack={() => {
            if (state.activeId) {
              navigate(sessionPath(state.activeId));
            } else {
              navigate(NEW_TASK_PATH);
            }
          }}
          onSelectDetailExpert={setDetailExpert}
          onRequestSummon={(expert) => {
            setSelectedExpertId(expert.id);
            setSummonExpert(expert);
          }}
          onPlaybookSelect={(playbook) => void handleInspirationLaunch(playbook, true)}
          marketSkills={marketSkills}
          marketConnectors={marketConnectors}
          onMarketSkillsChange={setMarketSkills}
          onMarketConnectorsChange={setMarketConnectors}
          onExpertsRefresh={() => {
            void listExperts().then(setExperts).catch(() => undefined);
          }}
          isNewTask={isNewTask}
          welcomeHydrated={welcomeHydrated}
          inputDockConfig={inputDockConfig}
          newTaskDraft={newTaskDraft}
          onInspirationLaunch={handleInspirationLaunch}
          onDiscoverLaunch={handleDiscoverLaunch}
          activeSession={activeSession}
          activeTeamHistoryLoading={activeTeamHistoryLoading}
          linkedCloudSessionStatus={linkedCloudSession?.status ?? null}
          onCloudBadgeClick={
            linkedCloudSession ? () => setCloudDrawerOpen(true) : undefined
          }
          chatItems={chatItems}
          activeStreaming={activeStreaming}
          activeStreamStage={activeStreamStage}
          assistantBrand={assistantBrand}
          onConfirmPlan={handlePlanConfirm}
          confirmingPlan={confirmingPlan}
          onUpdatePlanSteps={handlePlanUpdate}
          savingPlan={savingPlan}
          activeTeam={activeTeam}
          activeExpert={activeExpert ?? null}
          approvalBusy={approvalBusy}
          questionBusy={questionBusy}
          onQuestionAnswer={handleQuestionAnswer}
          onQuestionSkip={(questionId) => void handleQuestionAnswer(questionId, [], undefined, true)}
          onApprovalDecide={handleApprovalDecide}
          onEditMessage={(seq, text) => {
            if (state.activeId) {
              void editMessage(state.activeId, seq, text);
            }
          }}
          onRetry={(fromSeq) => {
            if (state.activeId) {
              void retryMessage(state.activeId, fromSeq);
            }
          }}
          activeRunError={activeRunError}
          onDismissRunError={() => {
            if (state.activeId) {
              clearSessionError(state.activeId);
            }
          }}
          onRetryRunError={
            state.activeId && activeRetryFromSeq != null && !activeStreaming
              ? () => {
                  void retryMessage(state.activeId!, activeRetryFromSeq);
                }
              : undefined
          }
          memoryStatus={memoryStatus}
          onRememberSession={() => void handleRememberSession()}
          rememberSessionBusy={rememberSessionBusy}
          canRememberSession={canRememberSession}
          activeQueueDepth={activeQueueDepth}
          onClearQueue={
            state.activeId && activeQueueDepth > 0 && !activeStreaming
              ? () => {
                  setClearQueueBusy(true);
                  void clearQueue(state.activeId!).finally(() => setClearQueueBusy(false));
                }
              : undefined
          }
          clearQueueBusy={clearQueueBusy}
          onShare={() => setShareDialogOpen(true)}
          shareDialogOpen={shareDialogOpen}
          shareToast={shareToast}
          onOpenArtifact={detailPanel.handleOpenArtifact}
          onOpenChanges={detailPanel.handleOpenChanges}
          onOpenDetailTab={detailPanel.handleOpenDetailTab}
          onArchive={
            state.activeId
              ? (archived) => void handleSessionMetadataChange(state.activeId!, { archived })
              : undefined
          }
          sidecarImport={sidecarImport}
          detailPanelVisible={detailPanel.detailPanelVisible}
          detailPanelAvailable={showArtifactPanel && detailPanel.detailPanelSupported}
          onToggleDetailPanel={detailPanel.handleToggleDetailPanel}
          onChangeExpert={
            state.activeId && activeSession
              ? () => setChangeExpertOpen(true)
              : undefined
          }
          artifactRefreshKey={artifactRefreshKey}
          onOpenTeamMember={detailPanel.handleOpenTeamMember}
          onMemberFocusChange={detailPanel.setDetailMemberFocus}
          detailPanelEnabled={detailPanelEnabled}
          detailPanelWidth={detailPanel.detailPanelWidth}
          onDetailPanelWidthChange={detailPanel.handleDetailPanelWidthChange}
          artifactAutoOpenPath={detailPanel.artifactAutoOpenPath}
          artifactAutoOpenMode={detailPanel.artifactAutoOpenMode}
          onArtifactAutoOpenHandled={() => {
            detailPanel.setArtifactAutoOpenPath(null);
            detailPanel.setArtifactAutoOpenMode(null);
          }}
          detailFocusTab={detailPanel.detailFocusTab}
          onFocusTabHandled={() => detailPanel.setDetailFocusTab(null)}
          detailMemberFocus={detailPanel.detailMemberFocus}
          onFocusMemberHandled={() => detailPanel.setDetailMemberFocus(null)}
          onOpenMyFiles={() => navigate(MY_FILES_PATH)}
          onOpenMarketExperts={() => openMarket('experts')}
          onOpenMarketConnectors={() => openMarket('connectors')}
          onMoreDiscoverLaunch={handleDiscoverLaunch}
          onOpenAudit={() => navigate(AUDIT_LOG_PATH)}
        />
      </WorkbenchLayout>
      <AppShellOverlays
        modalPending={modalPending}
        approvalBusy={approvalBusy}
        activeExpert={activeExpert ?? null}
        activeId={state.activeId}
        onModalDecide={(decision, scope) => {
          if (!activePending) {
            return;
          }
          void handleApprovalDecide(activePending.approvalId, decision, scope);
        }}
        onModalClose={() => {
          if (!state.activeId) {
            return;
          }
          setPendingBySession((prev) => {
            if (!prev[state.activeId!]) {
              return prev;
            }
            const next = { ...prev };
            delete next[state.activeId!];
            return next;
          });
        }}
        shareDialogOpen={shareDialogOpen}
        activeSessionTitle={activeSession?.title ?? '当前任务'}
        onShareDialogClose={() => setShareDialogOpen(false)}
        onShared={async (_link, url) => {
          try {
            await navigator.clipboard.writeText(url);
            setShareToast('分享链接已复制');
          } catch {
            setShareToast('链接已生成，请手动复制');
          }
          setShareDialogOpen(false);
        }}
        changeExpertOpen={changeExpertOpen}
        experts={experts}
        activeSession={activeSession}
        workspacePresets={workspacePresets}
        changeExpertBusy={changeExpertBusy}
        creatingSession={creatingSession}
        onChangeExpertClose={() => setChangeExpertOpen(false)}
        onChangeExpertConfirm={handleChangeExpertConfirm}
        cloudDrawerOpen={cloudDrawerOpen}
        linkedCloudSession={linkedCloudSession}
        onCloudDrawerClose={() => setCloudDrawerOpen(false)}
        onOpenAutomation={() => navigate(AUTOMATION_PATH)}
        maxSessionsDialog={maxSessionsDialog}
        autoArchiveEnabled={autoArchiveEnabled}
        sessions={state.sessions}
        onArchiveFromLimitDialog={handleArchiveFromLimitDialog}
        onAutoArchiveBulk={handleAutoArchiveBulk}
        onMaxSessionsDismiss={() => setMaxSessionsDialog(null)}
        summonExpert={summonExpert}
        summonBusy={summonBusy}
        summonContinueSession={isOpenSessionView && Boolean(state.activeId) && !activeSession?.archivedAt}
        onSummonConfirm={() => void handleSummonConfirm()}
        onSummonCancel={() => setSummonExpert(null)}
      />
    </div>
  );
}
