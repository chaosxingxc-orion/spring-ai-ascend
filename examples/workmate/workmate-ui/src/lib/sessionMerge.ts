import type { Session } from '../types/api';

function withSessionDefaults(partial: Session): Session {
  // Spread first so provided values win, then backfill defaults for any field that arrives
  // null/undefined from the API despite the static type.
  return {
    ...partial,
    workspaceRoot: partial.workspaceRoot ?? '',
    permissionMode: partial.permissionMode ?? 'CRAFT',
    title: partial.title ?? '未命名任务',
    status: partial.status ?? 'CREATED',
    createdAt: partial.createdAt ?? partial.updatedAt,
  };
}

/** Fields returned by GET /api/v1/sessions/summary — overlay without dropping hydrated detail. */
function overlaySummary(prev: Session, summary: Session): Session {
  return {
    ...prev,
    id: summary.id,
    title: summary.title,
    workspaceKey: summary.workspaceKey,
    status: summary.status,
    expertId: summary.expertId,
    permissionMode: summary.permissionMode,
    modelId: summary.modelId ?? prev.modelId,
    effort: summary.effort ?? prev.effort,
    createdAt: summary.createdAt,
    updatedAt: summary.updatedAt,
    pinned: summary.pinned,
    archivedAt: summary.archivedAt,
  };
}

/** Merge lightweight list rows into existing state, preserving hydrated fields (workspaceRoot, tokens, …). */
export function mergeSessionSummaries(previous: Session[], summaries: Session[]): Session[] {
  const previousById = new Map(previous.map((session) => [session.id, session]));
  return summaries.map((summary) => {
    const prev = previousById.get(summary.id);
    if (!prev) {
      return withSessionDefaults(summary);
    }
    return overlaySummary(prev, summary);
  });
}

/** Patch a single session after getSession / metadata PATCH / create. */
export function upsertSession(sessions: Session[], session: Session): Session[] {
  const index = sessions.findIndex((item) => item.id === session.id);
  if (index < 0) {
    return [withSessionDefaults(session), ...sessions];
  }
  const next = [...sessions];
  next[index] = { ...next[index], ...session };
  return next;
}
