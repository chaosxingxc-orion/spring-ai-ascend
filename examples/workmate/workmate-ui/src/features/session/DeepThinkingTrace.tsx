import { useState } from 'react';
import type { ChatItem, ToolChatItem } from '../../types/events';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import { classifyTool, toolStepIcon, toolStepLabel } from '../../lib/toolKind';
import { isToolInProgress } from '../../lib/toolStatus';

interface ThinkingStep {
  id: string;
  label: string;
  icon: string;
  status: ToolChatItem['status'];
}

function collectToolSteps(
  items: ChatItem[],
  labels?: TeamUiLabels,
  memberLabelForTopic?: (topic: string) => string | undefined,
): ThinkingStep[] {
  const steps: ThinkingStep[] = [];
  const stepOptions = labels ? { labels, memberLabelForTopic } : undefined;
  for (const item of items) {
    if (item.kind !== 'tool') {
      continue;
    }
    const kind = classifyTool(item.toolName);
    steps.push({
      id: item.id,
      label: toolStepLabel(item.toolName, item.args, stepOptions),
      icon: toolStepIcon(kind),
      status: item.status,
    });
  }
  return steps;
}

interface DeepThinkingTraceProps {
  items?: ChatItem[];
  reasoningText?: string;
  streaming?: boolean;
  labels?: TeamUiLabels;
  memberLabelForTopic?: (topic: string) => string | undefined;
}

/** 深度思考 — merged leader reasoning + optional compact tool steps. */
export function DeepThinkingTrace({
  items = [],
  reasoningText = '',
  streaming,
  labels,
  memberLabelForTopic,
}: DeepThinkingTraceProps) {
  const [expanded, setExpanded] = useState(true);
  const steps = collectToolSteps(items, labels, memberLabelForTopic);
  const trimmedReasoning = reasoningText.trim();
  const hasReasoning = Boolean(trimmedReasoning);
  const showTrace = hasReasoning || steps.length > 0 || streaming;

  if (!showTrace) {
    return null;
  }

  return (
    <div className="deep-thinking-trace">
      <button
        type="button"
        className="deep-thinking-trace-header"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
      >
        <span className="task-deep-thinking">深度思考</span>
        <span className="deep-thinking-chevron" aria-hidden>{expanded ? '▾' : '▸'}</span>
      </button>
      {expanded && (
        <>
          {hasReasoning && (
            <div className="deep-thinking-reasoning muted">{trimmedReasoning}</div>
          )}
          {!hasReasoning && streaming && (
            <div className="deep-thinking-reasoning muted">思考中…</div>
          )}
          {steps.length > 0 && (
            <ol className="deep-thinking-steps">
              {steps.map((step) => (
                <li key={step.id} className={`deep-thinking-step status-${step.status}`}>
                  <span className="deep-thinking-step-icon" aria-hidden>{step.icon}</span>
                  <span className="deep-thinking-step-label">{step.label}</span>
                  {isToolInProgress(step.status) && (
                    <span className="deep-thinking-step-spinner" aria-hidden>…</span>
                  )}
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </div>
  );
}
