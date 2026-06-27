import type { ToolStatus } from '../../types/events';
import type { BusLaneHighlight } from '../../lib/busLaneHighlight';
import { findBusLaneHighlight, isSameBusLaneHighlight } from '../../lib/busLaneHighlight';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import { resolveTeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamBusEntry } from '../../lib/teamStatus';
import { classifyTool, busPublishPreviewFromArgs } from '../../lib/toolKind';
import { BashToolCard } from './BashToolCard';
import { DefaultToolCard } from './DefaultToolCard';
import { DeleteFilesToolCard } from './DeleteFilesToolCard';
import { ListFilesToolCard } from './ListFilesToolCard';
import { McpToolCard } from './McpToolCard';
import { ReadToolCard } from './ReadToolCard';
import { TeamBusPublishToolCard } from './TeamBusPublishToolCard';
import { TeamBuildToolCard } from './TeamBuildToolCard';
import { TeamReceiveMessageToolCard } from './TeamReceiveMessageToolCard';
import { TeamSendMessageToolCard } from './TeamSendMessageToolCard';
import { WebFetchToolCard } from './WebFetchToolCard';
import { WebSearchToolCard } from './WebSearchToolCard';
import { WriteToolCard } from './WriteToolCard';
import { SkillProgressToolCard } from './SkillProgressToolCard';

interface ToolRendererProps {
  toolName: string;
  status: ToolStatus;
  args?: unknown;
  result?: unknown;
  labels?: TeamUiLabels;
  memberLabelForTopic?: (topic: string) => string | undefined;
  memberLabel?: (memberId: string) => string | undefined;
  occurredAt?: number;
  busLaneHighlight?: BusLaneHighlight | null;
  busLanes?: Record<string, TeamBusEntry[]>;
  onBusPublishHighlight?: (topic: string, preview?: string) => void;
  onOpenChanges?: (path: string) => void;
  memberId?: string;
}

/** W15c — 按工具类型分派渲染 */
export function ToolRenderer({
  toolName,
  status,
  args,
  result,
  labels,
  memberLabelForTopic,
  memberLabel,
  occurredAt,
  busLaneHighlight,
  busLanes,
  onBusPublishHighlight,
  onOpenChanges,
  memberId,
}: ToolRendererProps) {
  const resolvedLabels = labels ?? resolveTeamUiLabels(null);
  const kind = classifyTool(toolName, args, { memberId });

  switch (kind) {
    case 'bash':
      return <BashToolCard status={status} args={args} result={result} />;
    case 'write':
      return (
        <WriteToolCard
          status={status}
          args={args}
          result={result}
          onOpenChanges={onOpenChanges}
        />
      );
    case 'read':
      return <ReadToolCard status={status} args={args} result={result} />;
    case 'list':
      return <ListFilesToolCard status={status} args={args} result={result} />;
    case 'delete':
      return <DeleteFilesToolCard status={status} args={args} result={result} />;
    case 'web-search':
      return <WebSearchToolCard status={status} args={args} result={result} />;
    case 'web-fetch':
      return <WebFetchToolCard status={status} args={args} result={result} />;
    case 'skill':
      return (
        <SkillProgressToolCard
          toolName={toolName}
          status={status}
          args={args}
          result={result}
        />
      );
    case 'mcp':
      return <McpToolCard toolName={toolName} status={status} args={args} result={result} />;
    case 'team-bus-publish': {
      const { topic, preview } = busPublishPreviewFromArgs(args);
      const laneMatch = busLanes && topic ? findBusLaneHighlight(busLanes, topic, preview) : null;
      const laneLinked = isSameBusLaneHighlight(busLaneHighlight, laneMatch);
      const authorLabel = topic ? memberLabelForTopic?.(topic) : undefined;
      return (
        <TeamBusPublishToolCard
          status={status}
          args={args}
          result={result}
          labels={resolvedLabels}
          authorLabel={authorLabel}
          occurredAt={occurredAt}
          laneLinked={laneLinked}
          onHighlightLane={
            onBusPublishHighlight && topic
              ? () => onBusPublishHighlight(topic, preview)
              : undefined
          }
        />
      );
    }
    case 'team-build':
      return <TeamBuildToolCard status={status} args={args} result={result} />;
    case 'team-send-message':
      return (
        <TeamSendMessageToolCard
          status={status}
          args={args}
          memberLabel={memberLabel ?? memberLabelForTopic}
        />
      );
    case 'team-receive-message':
      return (
        <TeamReceiveMessageToolCard
          status={status}
          args={args}
          result={result}
          senderLabel={memberId ? memberLabel?.(memberId) : undefined}
          memberLabel={memberLabel ?? memberLabelForTopic}
        />
      );
    default:
      return (
        <DefaultToolCard toolName={toolName} status={status} args={args} result={result} />
      );
  }
}
