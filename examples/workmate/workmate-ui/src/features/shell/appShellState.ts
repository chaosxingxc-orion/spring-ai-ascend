import { applyChatAction, type ChatAction } from '../../state/chatState';
import { loadChat, loadActiveSessionId, saveActiveSessionId } from '../../state/chatStorage';
import { mergeServerChatWithLocal } from '../../lib/chatMerge';
import { mergeSessionSummaries, upsertSession } from '../../lib/sessionMerge';
import type { Session } from '../../types/api';
import type { ChatItem } from '../../types/events';

export interface AppState {
  sessions: Session[];
  activeId: string | null;
  chatBySession: Record<string, ChatItem[]>;
}

export type AppShellAction =
  | { type: 'set-sessions'; sessions: Session[] }
  | { type: 'upsert-session'; session: Session }
  | { type: 'select'; id: string }
  | { type: 'clear-select' }
  | {
      type: 'update-session-usage';
      sessionId: string;
      promptTokens: number;
      completionTokens: number;
    }
  | { type: 'hydrate-chat'; sessionId: string; items: ChatItem[] }
  | { type: 'set-chat-from-server'; sessionId: string; items: ChatItem[] }
  | ChatAction;

export interface LocationState {
  initialMessage?: string;
  draftSeed?: string;
  expertId?: string;
}

function mergeSessionUsage(
  sessions: Session[],
  sessionId: string,
  promptTokens: number,
  completionTokens: number,
): Session[] {
  return sessions.map((session) =>
    session.id === sessionId
      ? { ...session, promptTokens, completionTokens }
      : session,
  );
}

export function appShellReducer(state: AppState, action: AppShellAction): AppState {
  switch (action.type) {
    case 'set-sessions':
      return { ...state, sessions: mergeSessionSummaries(state.sessions, action.sessions) };
    case 'upsert-session':
      return { ...state, sessions: upsertSession(state.sessions, action.session) };
    case 'select': {
      saveActiveSessionId(action.id);
      return { ...state, activeId: action.id };
    }
    case 'clear-select':
      saveActiveSessionId('');
      return { ...state, activeId: null };
    case 'update-session-usage':
      return {
        ...state,
        sessions: mergeSessionUsage(
          state.sessions,
          action.sessionId,
          action.promptTokens,
          action.completionTokens,
        ),
      };
    case 'hydrate-chat':
      if (state.chatBySession[action.sessionId]?.length) {
        return state;
      }
      return {
        ...state,
        chatBySession: { ...state.chatBySession, [action.sessionId]: action.items },
      };
    case 'set-chat-from-server':
      return {
        ...state,
        chatBySession: {
          ...state.chatBySession,
          [action.sessionId]: mergeServerChatWithLocal(
            state.chatBySession[action.sessionId] ?? [],
            action.items,
          ),
        },
      };
    default:
      return {
        ...state,
        chatBySession: applyChatAction(state.chatBySession, action),
      };
  }
}

function readRouteSessionId(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const match = window.location.pathname.match(/^\/s\/([^/]+)/);
  return match?.[1] ?? null;
}

/** Hydrate active session + cached chat from URL/localStorage before first paint. */
export function createInitialAppState(): AppState {
  const routeSessionId = readRouteSessionId();
  const activeId = routeSessionId ?? loadActiveSessionId();
  const chatBySession: Record<string, ChatItem[]> = {};
  if (activeId) {
    const stored = loadChat(activeId);
    if (stored.length > 0) {
      chatBySession[activeId] = stored;
    }
  }
  return { sessions: [], activeId, chatBySession };
}

export const initialAppState: AppState = {
  sessions: [],
  activeId: null,
  chatBySession: {},
};
