import type { BusLaneHighlight } from '../../lib/busLaneHighlight';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamState } from '../../lib/teamStatus';
import { resolveTeamVisualizationLayout } from '../../lib/teamVisualizationRoute';
import { AgentTeamGrid } from './AgentTeamGrid';
import { EventLanes } from './EventLanes';
import { GeneratorVerifierLoop } from './GeneratorVerifierLoop';
import { SharedBlackboard } from './SharedBlackboard';
import { TeamCollaborationSummary } from './TeamCollaborationSummary';
import { TeamDelegationBar } from './TeamDelegationBar';
import { TeamStepper, type TeamStepperMemberRef } from './TeamStepper';

interface TeamVisualizationProps {
  team: TeamState | null | undefined;
  labels: TeamUiLabels;
  busLaneHighlight?: BusLaneHighlight | null;
  onBusLaneSelect?: (highlight: BusLaneHighlight) => void;
  onSelectMember?: (member: TeamStepperMemberRef) => void;
  blackboardPath?: string | null;
  onOpenBlackboard?: (path: string) => void;
}

/**
 * 协同拓扑可视化分发器（ADR-013 接缝）。
 * 按 `team.pattern` + `team.collaboration` 选择形态；不改 SessionChatView 与上游数据流。
 */
export function TeamVisualization({
  team,
  labels,
  busLaneHighlight,
  onBusLaneSelect,
  onSelectMember,
  blackboardPath,
  onOpenBlackboard,
}: TeamVisualizationProps) {
  if (!team) {
    return null;
  }
  const layout = resolveTeamVisualizationLayout(team);
  const stepperProps = {
    team,
    defaultCollapsed: team.phase === 'done',
    onSelectMember,
  };
  const summary = team.phase === 'done' ? (
    <TeamCollaborationSummary
      team={team}
      blackboardPath={blackboardPath}
      onSelectMember={onSelectMember}
      onOpenBlackboard={onOpenBlackboard}
    />
  ) : null;

  if (layout === 'delegation') {
    return (
      <>
        <TeamDelegationBar team={team} onSelectMember={onSelectMember} />
        {summary}
      </>
    );
  }
  if (layout === 'agent-grid') {
    return (
      <>
        <AgentTeamGrid team={team} labels={labels} />
        {summary}
      </>
    );
  }
  if (layout === 'stepper') {
    return (
      <>
        <TeamStepper {...stepperProps} />
        {summary}
      </>
    );
  }
  switch (team.pattern) {
    case 'generator-verifier':
      return (
        <>
          <GeneratorVerifierLoop team={team} labels={labels} />
          {summary}
        </>
      );
    case 'message-bus':
      return (
        <>
          <EventLanes
            team={team}
            labels={labels}
            busLaneHighlight={busLaneHighlight}
            onBusLaneSelect={onBusLaneSelect}
          />
          {summary}
        </>
      );
    case 'shared-state':
      return (
        <>
          <SharedBlackboard team={team} labels={labels} />
          {summary}
        </>
      );
    default:
      return (
        <>
          <TeamStepper {...stepperProps} />
          {summary}
        </>
      );
  }
}
