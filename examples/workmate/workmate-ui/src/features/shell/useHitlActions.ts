import { useCallback, useState } from 'react';
import { answerQuestion, decideApproval } from '../../api/client';
import { isBusinessApprovalTool } from '../../lib/businessApproval';
import type { AppShellAction, AppState } from './appShellState';
import type {
  ApprovalDecision,
  ApprovalDecisionScope,
} from '../../types/api';
import type { ApprovalRequiredPayload } from '../../types/events';

export function useHitlActions(
  state: AppState,
  dispatch: React.Dispatch<AppShellAction>,
  pendingBySession: Record<string, ApprovalRequiredPayload>,
  setPendingBySession: React.Dispatch<
    React.SetStateAction<Record<string, ApprovalRequiredPayload>>
  >,
  resumeRunAfterHitl: (sessionId: string) => void,
  setLoadError: React.Dispatch<React.SetStateAction<string | null>>,
) {
  const [approvalBusy, setApprovalBusy] = useState(false);
  const [questionBusy, setQuestionBusy] = useState(false);

  const handleApprovalDecide = useCallback(
    async (
      approvalId: string,
      decision: ApprovalDecision,
      scope?: ApprovalDecisionScope,
    ) => {
      let sessionId: string | undefined;
      for (const [sid, items] of Object.entries(state.chatBySession)) {
        if (
          items.some(
            (item) => item.kind === 'approval' && item.approvalId === approvalId,
          )
        ) {
          sessionId = sid;
          break;
        }
      }
      if (!sessionId) {
        const pending = Object.values(pendingBySession).find(
          (item) => item.approvalId === approvalId,
        );
        sessionId = pending?.sessionId;
      }
      if (!sessionId) {
        return;
      }
      setApprovalBusy(true);
      try {
        await decideApproval(approvalId, decision, scope);
        const pending =
          pendingBySession[sessionId] ??
          Object.values(pendingBySession).find((item) => item.approvalId === approvalId);
        if (decision === 'approve' && pending && !isBusinessApprovalTool(pending.tool)) {
          dispatch({
            type: 'tool-resume',
            sessionId,
            toolName: pending.tool,
            toolCallId: pending.toolCallId,
          });
        }
        dispatch({
          type: 'resolve-approval',
          sessionId,
          approvalId,
          decision,
        });
        setPendingBySession((prev) => {
          const next = { ...prev };
          delete next[sessionId!];
          return next;
        });
        void resumeRunAfterHitl(sessionId);
      } catch (error) {
        const message = (error as Error).message ?? '';
        const approvalGone = /approval not found/i.test(message) || message.includes('404');
        if (approvalGone) {
          dispatch({
            type: 'resolve-approval',
            sessionId,
            approvalId,
            decision: 'deny',
          });
          setPendingBySession((prev) => {
            const next = { ...prev };
            delete next[sessionId!];
            return next;
          });
          return;
        }
        setLoadError(message);
      } finally {
        setApprovalBusy(false);
      }
    },
    [dispatch, pendingBySession, resumeRunAfterHitl, setLoadError, state.chatBySession],
  );

  const handleQuestionAnswer = useCallback(
    async (
      questionId: string,
      selections: string[],
      text?: string,
      skip = false,
    ) => {
      let sessionId: string | undefined;
      for (const [sid, items] of Object.entries(state.chatBySession)) {
        if (items.some((item) => item.kind === 'question' && item.questionId === questionId)) {
          sessionId = sid;
          break;
        }
      }
      if (!sessionId) {
        sessionId = state.activeId ?? undefined;
      }
      if (!sessionId) {
        return;
      }
      setQuestionBusy(true);
      dispatch({
        type: 'resolve-question',
        sessionId,
        questionId,
        status: skip ? 'skipped' : 'answered',
        selections: skip ? [] : selections,
        answerText: skip ? undefined : text,
      });
      try {
        await answerQuestion(sessionId, questionId, { selections, text, skip });
        dispatch({
          type: 'tool-resume',
          sessionId,
          toolName: `workmate_ask_user_question__${sessionId}`,
        });
        void resumeRunAfterHitl(sessionId);
      } catch (error) {
        const message = (error as Error).message ?? '';
        const questionGone = /question not found/i.test(message) || message.includes('404');
        if (questionGone) {
          dispatch({
            type: 'resolve-question',
            sessionId,
            questionId,
            status: 'cancelled',
            selections: [],
            answerText: undefined,
          });
          return;
        }
        dispatch({
          type: 'resolve-question',
          sessionId,
          questionId,
          status: 'pending',
          selections: [],
          answerText: undefined,
        });
        setLoadError(message);
      } finally {
        setQuestionBusy(false);
      }
    },
    [dispatch, resumeRunAfterHitl, setLoadError, state.activeId, state.chatBySession],
  );

  return {
    approvalBusy,
    questionBusy,
    handleApprovalDecide,
    handleQuestionAnswer,
  };
}
