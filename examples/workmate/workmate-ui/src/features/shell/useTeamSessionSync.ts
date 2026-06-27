import { useCallback, useEffect, useState } from 'react';
import { getTeamSnapshot, listSessionRunEvents } from '../../api/client';
import { hydrateSessionChat } from '../../lib/sessionChatLoad';
import { mapRecordedEventsToRunEventRows } from '../../lib/memberSurfaceHydrate';
import { isLeaderRunTerminal } from '../../lib/memberEventProjection';
import type { Expert, Session } from '../../types/api';

interface UseTeamSessionSyncOptions {
  isOpenSessionView: boolean;
  routeSessionId: string | null;
  sessions: Session[];
  experts: Expert[];
  streamingBySession: Record<string, boolean>;
  hydrateTeamFromEvents: (
    sessionId: string,
    events: Awaited<ReturnType<typeof listSessionRunEvents>>,
    options?: {
      snapshot?: Awaited<ReturnType<typeof getTeamSnapshot>> | null;
      expertTeamRuntime?: string | null;
    },
  ) => void;
  applyTeamSnapshot: (
    sessionId: string,
    snapshot: NonNullable<Awaited<ReturnType<typeof getTeamSnapshot>>>,
  ) => void;
  resumeTeamRunIfActive: (sessionId: string) => Promise<boolean | void>;
  refreshMemberSurfaceFromEvents: (
    sessionId: string,
    events: ReturnType<typeof mapRecordedEventsToRunEventRows>,
  ) => void;
  syncChatFromServer: (sessionId: string) => Promise<void>;
  teamEventsLoadedRef: React.MutableRefObject<Set<string>>;
  teamEventsCacheRef: React.MutableRefObject<
    Record<string, Awaited<ReturnType<typeof listSessionRunEvents>>>
  >;
  teamPollSeqRef: React.MutableRefObject<Record<string, number>>;
  serverChatLoadedRef: React.MutableRefObject<Set<string>>;
}

export function useTeamSessionSync({
  isOpenSessionView,
  routeSessionId,
  sessions,
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
}: UseTeamSessionSyncOptions) {
  const [teamHistoryLoadingBySession, setTeamHistoryLoadingBySession] = useState<
    Record<string, boolean>
  >({});

  const setTeamHistoryLoading = useCallback((sessionId: string, loading: boolean) => {
    setTeamHistoryLoadingBySession((prev) => {
      if (!!prev[sessionId] === loading) {
        return prev;
      }
      if (!loading) {
        const next = { ...prev };
        delete next[sessionId];
        return next;
      }
      return { ...prev, [sessionId]: true };
    });
  }, []);

  useEffect(() => {
    if (!isOpenSessionView || !routeSessionId) {
      return;
    }
    const sessionId = routeSessionId;
    const session = sessions.find((s) => s.id === sessionId);
    if (!session?.expertId) {
      return;
    }
    const expert = experts.find((e) => e.id === session.expertId);
    if (experts.length > 0 && expert?.expertType !== 'team') {
      return;
    }
    const expertTeamRuntime = expert?.teamRuntime ?? null;

    if (teamEventsLoadedRef.current.has(sessionId)) {
      return;
    }
    teamEventsLoadedRef.current.add(sessionId);
    setTeamHistoryLoading(sessionId, true);
    void (async () => {
      try {
        const [hydration, snapshot] = await Promise.all([
          hydrateSessionChat(sessionId),
          getTeamSnapshot(sessionId).catch(() => null),
        ]);
        const { events } = hydration;
        teamEventsCacheRef.current[sessionId] = events;
        if (events.length > 0) {
          hydrateTeamFromEvents(sessionId, events, { snapshot, expertTeamRuntime });
          teamPollSeqRef.current[sessionId] = events[events.length - 1]?.seq ?? 0;
          refreshMemberSurfaceFromEvents(sessionId, mapRecordedEventsToRunEventRows(events));
          void resumeTeamRunIfActive(sessionId);
        } else if (snapshot) {
          applyTeamSnapshot(sessionId, snapshot);
        }
      } catch {
        teamEventsLoadedRef.current.delete(sessionId);
      } finally {
        setTeamHistoryLoading(sessionId, false);
      }
    })();
  }, [
    isOpenSessionView,
    routeSessionId,
    sessions,
    experts,
    hydrateTeamFromEvents,
    applyTeamSnapshot,
    resumeTeamRunIfActive,
    refreshMemberSurfaceFromEvents,
    setTeamHistoryLoading,
    teamEventsLoadedRef,
    teamEventsCacheRef,
    teamPollSeqRef,
  ]);

  useEffect(() => {
    if (!isOpenSessionView || !routeSessionId) {
      return undefined;
    }
    const sessionId = routeSessionId;
    const session = sessions.find((s) => s.id === sessionId);
    const expert = session?.expertId ? experts.find((e) => e.id === session.expertId) : null;
    if (experts.length > 0 && expert?.expertType !== 'team') {
      return undefined;
    }
    const expertTeamRuntime = expert?.teamRuntime ?? null;

    let cancelled = false;
    let timer: number | undefined;

    const syncFromServer = async (): Promise<boolean> => {
      try {
        const prevSeq = teamPollSeqRef.current[sessionId] ?? 0;
        const baseline = teamEventsCacheRef.current[sessionId] ?? [];
        const canIncrement = prevSeq > 0 && baseline.length > 0;
        const fetched = canIncrement
          ? await listSessionRunEvents(sessionId, prevSeq)
          : await listSessionRunEvents(sessionId);
        if (cancelled) {
          return false;
        }
        const events = canIncrement
          ? (fetched.length > 0 ? [...baseline, ...fetched] : baseline)
          : fetched;
        if (events.length === 0) {
          return false;
        }
        teamEventsCacheRef.current[sessionId] = events;
        const names = events.map((event) => event.name);
        if (!names.includes('team.started')) {
          return false;
        }
        const lastSeq = events[events.length - 1]?.seq ?? 0;
        if (lastSeq > prevSeq) {
          teamPollSeqRef.current[sessionId] = lastSeq;
          const snapshot = await getTeamSnapshot(sessionId).catch(() => null);
          if (!cancelled) {
            hydrateTeamFromEvents(sessionId, events, {
              snapshot,
              expertTeamRuntime,
            });
            refreshMemberSurfaceFromEvents(sessionId, mapRecordedEventsToRunEventRows(events));
            if (!streamingBySession[sessionId]) {
              serverChatLoadedRef.current.delete(sessionId);
              void syncChatFromServer(sessionId);
            }
          }
        }
        return events.some((event) => isLeaderRunTerminal(event.name, event.data));
      } catch {
        return false;
      }
    };

    void syncFromServer().then((done) => {
      if (cancelled || done) {
        return;
      }
      timer = window.setInterval(() => {
        void syncFromServer().then((finished) => {
          if (finished && timer != null) {
            window.clearInterval(timer);
            timer = undefined;
          }
        });
      }, 1500);
    });

    return () => {
      cancelled = true;
      if (timer != null) {
        window.clearInterval(timer);
      }
    };
  }, [
    isOpenSessionView,
    routeSessionId,
    sessions,
    experts,
    hydrateTeamFromEvents,
    applyTeamSnapshot,
    syncChatFromServer,
    refreshMemberSurfaceFromEvents,
    streamingBySession,
    teamEventsCacheRef,
    teamPollSeqRef,
    serverChatLoadedRef,
  ]);

  return { teamHistoryLoadingBySession, setTeamHistoryLoading };
}
