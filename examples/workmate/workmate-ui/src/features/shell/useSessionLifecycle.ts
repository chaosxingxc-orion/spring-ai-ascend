import { useEffect, useLayoutEffect } from 'react';
import { invalidateSessionChatHydration } from '../../lib/sessionChatLoad';
import type { NavigateFunction } from 'react-router-dom';
import { getSession, listSessionRunEvents } from '../../api/client';
import { hydrateSessionChat } from '../../lib/sessionChatLoad';
import { NEW_TASK_PATH, sessionPath } from '../../lib/paths';
import { loadChat, saveChat } from '../../state/chatStorage';
import type { ChatItem } from '../../types/events';
import type { AppShellAction, AppState, LocationState } from './appShellState';

interface UseSessionRouteEffectsOptions {
  loadingSessions: boolean;
  sessionsLoadedRef: React.MutableRefObject<boolean>;
  routeSessionId: string | null;
  isShareReplay: boolean;
  isNewTask: boolean;
  pathname: string;
  locationState: unknown;
  locationSearch: string;
  searchParams: URLSearchParams;
  activeId: string | null;
  navigate: NavigateFunction;
  dispatch: React.Dispatch<AppShellAction>;
  setSelectedExpertId: React.Dispatch<React.SetStateAction<string>>;
  setNewTaskDraft: React.Dispatch<React.SetStateAction<string>>;
  pendingInitialMessageRef: React.MutableRefObject<string | null>;
  setArtifactAutoOpenPath: React.Dispatch<React.SetStateAction<string | null>>;
}

export function useSessionRouteEffects({
  loadingSessions,
  sessionsLoadedRef,
  routeSessionId,
  isShareReplay,
  isNewTask,
  pathname,
  locationState,
  locationSearch,
  searchParams,
  activeId,
  navigate,
  dispatch,
  setSelectedExpertId,
  setNewTaskDraft,
  pendingInitialMessageRef,
  setArtifactAutoOpenPath,
}: UseSessionRouteEffectsOptions) {
  useLayoutEffect(() => {
    if (!routeSessionId || isShareReplay || isNewTask) {
      return;
    }
    if (activeId !== routeSessionId) {
      dispatch({ type: 'select', id: routeSessionId });
    }
  }, [routeSessionId, isShareReplay, isNewTask, activeId, dispatch]);

  useEffect(() => {
    if (loadingSessions || !sessionsLoadedRef.current) {
      return;
    }

    if (pathname === '/') {
      navigate(NEW_TASK_PATH, { replace: true });
      return;
    }

    if (routeSessionId) {
      return;
    }

    if (activeId !== null) {
      dispatch({ type: 'clear-select' });
    }
  }, [loadingSessions, pathname, routeSessionId, navigate, activeId, dispatch, sessionsLoadedRef]);

  useEffect(() => {
    if (!routeSessionId || loadingSessions || isShareReplay || isNewTask) {
      return undefined;
    }
    let cancelled = false;
    void getSession(routeSessionId)
      .then((session) => {
        if (!cancelled) {
          dispatch({ type: 'upsert-session', session });
        }
      })
      .catch((err) => {
        if (!cancelled && /HTTP 404|not found/i.test((err as Error).message)) {
          navigate(NEW_TASK_PATH, { replace: true });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [routeSessionId, loadingSessions, isShareReplay, isNewTask, dispatch, navigate]);

  useEffect(() => {
    const routeState = locationState as LocationState | null;
    if (routeState?.expertId !== undefined) {
      setSelectedExpertId(routeState.expertId);
    }
    if (routeState?.draftSeed) {
      setNewTaskDraft(routeState.draftSeed);
    }
    if (routeState?.initialMessage && routeSessionId && activeId === routeSessionId) {
      pendingInitialMessageRef.current = routeState.initialMessage;
      navigate(pathname + locationSearch, { replace: true, state: {} });
    }
  }, [
    locationState,
    pathname,
    locationSearch,
    navigate,
    routeSessionId,
    activeId,
    setSelectedExpertId,
    setNewTaskDraft,
    pendingInitialMessageRef,
  ]);

  useEffect(() => {
    const filePath = searchParams.get('file');
    if (!filePath || !routeSessionId || activeId !== routeSessionId) {
      return;
    }
    setArtifactAutoOpenPath(filePath);
    const next = new URLSearchParams(searchParams);
    next.delete('file');
    const suffix = next.toString();
    navigate(`${sessionPath(routeSessionId)}${suffix ? `?${suffix}` : ''}`, { replace: true });
  }, [navigate, routeSessionId, searchParams, activeId, setArtifactAutoOpenPath]);
}

export function usePendingInitialMessage(
  pendingInitialMessageRef: React.MutableRefObject<string | null>,
  activeId: string | null,
  routeSessionId: string | null,
  runPrompt: (sessionId: string, message: string) => Promise<void>,
) {
  useEffect(() => {
    const message = pendingInitialMessageRef.current;
    if (!message || !activeId || routeSessionId !== activeId) {
      return;
    }
    pendingInitialMessageRef.current = null;
    void runPrompt(activeId, message);
  }, [activeId, routeSessionId, runPrompt, pendingInitialMessageRef]);
}

export function useSessionChatCleanup(
  activeId: string | null,
  serverChatLoadedRef: React.MutableRefObject<Set<string>>,
  teamEventsLoadedRef: React.MutableRefObject<Set<string>>,
) {
  useEffect(() => {
    const sessionId = activeId;
    if (!sessionId) {
      return undefined;
    }
    return () => {
      serverChatLoadedRef.current.delete(sessionId);
      teamEventsLoadedRef.current.delete(sessionId);
      invalidateSessionChatHydration(sessionId);
    };
  }, [activeId, serverChatLoadedRef, teamEventsLoadedRef]);
}

interface UseSessionChatSyncOptions {
  isOpenSessionView: boolean;
  routeSessionId: string | null;
  streamingBySession: Record<string, boolean>;
  chatBySession: AppState['chatBySession'];
  dispatch: React.Dispatch<AppShellAction>;
  serverChatLoadedRef: React.MutableRefObject<Set<string>>;
  teamEventsCacheRef: React.MutableRefObject<
    Record<string, Awaited<ReturnType<typeof import('../../api/client').listSessionRunEvents>>>
  >;
  reconcilePendingApprovals: (
    sessionId: string,
    options?: {
      events?: Awaited<ReturnType<typeof listSessionRunEvents>>;
      chatItems?: ChatItem[];
    },
  ) => Promise<void>;
  syncPendingQuestions: (sessionId: string) => Promise<void>;
}

export function useSessionChatSync({
  isOpenSessionView,
  routeSessionId,
  streamingBySession,
  chatBySession,
  dispatch,
  serverChatLoadedRef,
  teamEventsCacheRef,
  reconcilePendingApprovals,
  syncPendingQuestions,
}: UseSessionChatSyncOptions) {
  useEffect(() => {
    if (!isOpenSessionView || !routeSessionId) {
      return;
    }
    const sessionId = routeSessionId;
    if (streamingBySession[sessionId]) {
      return;
    }
    if (serverChatLoadedRef.current.has(sessionId)) {
      return;
    }

    const stored = loadChat(sessionId);
    if (stored.length > 0) {
      dispatch({ type: 'hydrate-chat', sessionId, items: stored });
    }

    void (async () => {
      try {
        const { fullItems, events } = await hydrateSessionChat(sessionId);
        teamEventsCacheRef.current[sessionId] = events;
        serverChatLoadedRef.current.add(sessionId);
        dispatch({ type: 'set-chat-from-server', sessionId, items: fullItems });
        void reconcilePendingApprovals(sessionId, { events, chatItems: fullItems });
        void syncPendingQuestions(sessionId);
      } catch {
        serverChatLoadedRef.current.add(sessionId);
        if (!chatBySession[sessionId]?.length && stored.length > 0) {
          dispatch({ type: 'hydrate-chat', sessionId, items: stored });
        }
      }
    })();
  }, [
    isOpenSessionView,
    routeSessionId,
    streamingBySession,
    chatBySession,
    dispatch,
    serverChatLoadedRef,
    teamEventsCacheRef,
    reconcilePendingApprovals,
    syncPendingQuestions,
  ]);

  useEffect(() => {
    if (!isOpenSessionView || !routeSessionId) {
      return;
    }
    const items = chatBySession[routeSessionId] ?? [];
    saveChat(routeSessionId, items);
  }, [isOpenSessionView, routeSessionId, chatBySession]);
}
