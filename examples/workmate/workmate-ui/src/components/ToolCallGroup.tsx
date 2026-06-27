import { useState } from 'react';
import type { ChatItem, ToolChatItem } from '../types/events';
import type { BusLaneHighlight } from '../lib/busLaneHighlight';
import type { TeamUiLabels } from '../lib/teamUiLabels';
import type { TeamBusEntry } from '../lib/teamStatus';
import { isToolFailed, isToolInProgress } from '../lib/toolStatus';
import { ToolCard } from './ToolCard';

interface ToolCallGroupProps {
  tools: ToolChatItem[];
  defaultExpanded?: boolean;
  labels?: TeamUiLabels;
  memberLabelForTopic?: (topic: string) => string | undefined;
  busLaneHighlight?: BusLaneHighlight | null;
  busLanes?: Record<string, TeamBusEntry[]>;
  onBusPublishHighlight?: (topic: string, preview?: string) => void;
  onOpenChanges?: (path: string) => void;
}

export function ToolCallGroup({
  tools,
  defaultExpanded,
  labels,
  memberLabelForTopic,
  busLaneHighlight,
  busLanes,
  onBusPublishHighlight,
  onOpenChanges,
}: ToolCallGroupProps) {
  const running = tools.some((tool) => isToolInProgress(tool.status));
  const failed = tools.some((tool) => isToolFailed(tool.status));
  const waiting = tools.some((tool) => tool.status === 'waiting');
  // A finished run of consecutive same-tool calls (common for members that fire many bash/write
  // steps) is collapsed by default so it reads as one compact "工具调用 (N)" row; active or failed
  // runs stay open so live progress and errors remain visible.
  const [expanded, setExpanded] = useState(defaultExpanded ?? (running || waiting || failed));

  const toolCardProps = {
    labels,
    memberLabelForTopic,
    busLaneHighlight,
    busLanes,
    onBusPublishHighlight,
    onOpenChanges,
  };

  if (tools.length === 1) {
    const tool = tools[0];
    return (
      <ToolCard
        toolName={tool.toolName}
        status={tool.status}
        args={tool.args}
        result={tool.result}
        occurredAt={tool.endedAt ?? tool.startedAt}
        {...toolCardProps}
      />
    );
  }

  return (
    <article className={`tool-call-group${running ? ' status-running status-executing' : ''}${waiting ? ' status-waiting' : ''}${failed ? ' status-error status-failed' : ''}`}>
      <button
        type="button"
        className="tool-call-group-header"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
      >
        <span className="tool-call-group-icon">⚙</span>
        <span className="tool-call-group-title">工具调用 ({tools.length})</span>
        <span className="tool-call-group-chevron">{expanded ? '▾' : '▸'}</span>
      </button>
      {expanded && (
        <div className="tool-call-group-body">
          {tools.map((tool) => (
            <ToolCard
              key={tool.id}
              toolName={tool.toolName}
              status={tool.status}
              args={tool.args}
              result={tool.result}
              occurredAt={tool.endedAt ?? tool.startedAt}
              {...toolCardProps}
            />
          ))}
        </div>
      )}
    </article>
  );
}

/**
 * Group *consecutive* tool calls that share the same context (same memberId,
 * treating `null`/`undefined` as the leader's own scope). Any non-tool item
 * (assistant/user/reasoning/etc.) breaks the run, so the original timeline
 * order is preserved and multiple independent groups can coexist within a
 * single turn.
 */
export function groupToolItems(items: ChatItem[]): ChatItem[] {
  const result: ChatItem[] = [];
  let toolRun: ToolChatItem[] = [];
  let runScope: string | null = null;
  let runToolName: string | null = null;

  const normalizeToolName = (toolName: string): string => toolName.trim().toLowerCase();
  // team.* orchestration tools (build_team / send_message) must render exactly at their
  // interaction step and never fold into a generic tool group.
  const isDelegationTeamTool = (toolName: string): boolean => {
    const normalized = normalizeToolName(toolName);
    return normalized.includes('build_team') || normalized.includes('send_message');
  };

  const flushTools = () => {
    if (toolRun.length === 0) {
      return;
    }
    if (toolRun.length === 1) {
      result.push(toolRun[0]);
    } else {
      result.push({
        id: `tool-group-${toolRun[0].id}`,
        kind: 'tool-group',
        tools: [...toolRun],
      });
    }
    toolRun = [];
    runScope = null;
    runToolName = null;
  };

  for (const item of items) {
    if (item.kind === 'tool') {
      const scope = item.memberId ?? '__leader__';
      const toolName = normalizeToolName(item.toolName);
      // Orchestration cards must stay standalone and render at their own step in the timeline.
      if (isDelegationTeamTool(item.toolName)) {
        flushTools();
        result.push(item);
        continue;
      }
      if (toolRun.length > 0 && (scope !== runScope || toolName !== runToolName)) {
        flushTools();
      }
      runScope = scope;
      runToolName = toolName;
      toolRun.push(item);
    } else {
      flushTools();
      result.push(item);
    }
  }
  flushTools();
  return result;
}
