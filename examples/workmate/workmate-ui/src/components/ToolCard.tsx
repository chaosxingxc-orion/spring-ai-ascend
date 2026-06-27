import type { ToolStatus } from '../types/events';
import type { BusLaneHighlight } from '../lib/busLaneHighlight';
import type { TeamUiLabels } from '../lib/teamUiLabels';
import type { TeamBusEntry } from '../lib/teamStatus';
import { ToolRenderer } from './tools/ToolRenderer';

interface ToolCardProps {
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

export function ToolCard({
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
}: ToolCardProps) {
  return (
    <ToolRenderer
      toolName={toolName}
      status={status}
      args={args}
      result={result}
      labels={labels}
      memberLabelForTopic={memberLabelForTopic}
      memberLabel={memberLabel}
      occurredAt={occurredAt}
      busLaneHighlight={busLaneHighlight}
      busLanes={busLanes}
      onBusPublishHighlight={onBusPublishHighlight}
      onOpenChanges={onOpenChanges}
      memberId={memberId}
    />
  );
}
