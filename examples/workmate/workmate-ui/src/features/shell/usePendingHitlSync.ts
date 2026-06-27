import { useCallback } from 'react';
import { listPendingApprovals, listPendingQuestions, listSessionRunEvents } from '../../api/client';
import { buildApprovalChatItem, isBusinessApprovalTool } from '../../lib/businessApproval';
import { modalPendingApprovalFromRunEvents } from '../../lib/approvalHydrate';
import { buildQuestionChatItem } from '../../lib/questionCard';
import { mapRecordedEventsToRunEventRows } from '../../lib/memberSurfaceHydrate';
import type { AppShellAction } from './appShellState';
import type { ApprovalRequiredPayload, ChatItem } from '../../types/events';

export function usePendingHitlSync(
  dispatch: React.Dispatch<AppShellAction>,
  setPendingBySession: React.Dispatch<
    React.SetStateAction<Record<string, ApprovalRequiredPayload>>
  >,
) {
  const syncPendingQuestions = useCallback(async (sessionId: string) => {
    try {
      const items = await listPendingQuestions(sessionId);
      if (items.length === 0) {
        return;
      }
      const first = items[0];
      dispatch({
        type: 'upsert-question',
        sessionId,
        item: buildQuestionChatItem({
          questionId: first.questionId,
          sessionId: first.sessionId,
          question: first.question,
          options: first.options ?? [],
          allowFreeText: first.allowFreeText,
          multiSelect: first.multiSelect,
          toolName: first.toolName,
        }),
      });
    } catch {
      // ignore refresh errors
    }
  }, [dispatch]);

  const syncPendingApprovals = useCallback(async (sessionId: string) => {
    try {
      const items = await listPendingApprovals(sessionId);
      if (items.length === 0) {
        setPendingBySession((prev) => {
          if (!prev[sessionId]) {
            return prev;
          }
          const next = { ...prev };
          delete next[sessionId];
          return next;
        });
        return;
      }
      const first = items[0];
      const payload: ApprovalRequiredPayload = {
        approvalId: first.approvalId,
        sessionId: first.sessionId,
        tool: first.toolName,
        risk: first.riskLevel,
        reason: first.reason,
        summary: first.summary,
        args: first.args ?? {},
      };
      if (isBusinessApprovalTool(payload.tool)) {
        dispatch({
          type: 'upsert-approval',
          sessionId,
          item: buildApprovalChatItem(payload),
        });
        setPendingBySession((prev) => {
          if (!prev[sessionId]) {
            return prev;
          }
          const next = { ...prev };
          delete next[sessionId];
          return next;
        });
        return;
      }
      setPendingBySession((prev) => ({
        ...prev,
        [sessionId]: payload,
      }));
    } catch {
      // ignore refresh errors
    }
  }, [dispatch, setPendingBySession]);

  const reconcilePendingApprovals = useCallback(
    async (
      sessionId: string,
      options?: {
        events?: Awaited<ReturnType<typeof listSessionRunEvents>>;
        chatItems?: ChatItem[];
      },
    ) => {
      if (options?.events) {
        const modal = modalPendingApprovalFromRunEvents(
          mapRecordedEventsToRunEventRows(options.events),
          options.chatItems ?? [],
        );
        if (modal) {
          setPendingBySession((prev) => ({
            ...prev,
            [sessionId]: { ...modal, sessionId: modal.sessionId ?? sessionId },
          }));
        } else {
          setPendingBySession((prev) => {
            if (!prev[sessionId]) {
              return prev;
            }
            const next = { ...prev };
            delete next[sessionId];
            return next;
          });
        }
      }
      await syncPendingApprovals(sessionId);
    },
    [setPendingBySession, syncPendingApprovals],
  );

  return { syncPendingQuestions, syncPendingApprovals, reconcilePendingApprovals };
}
