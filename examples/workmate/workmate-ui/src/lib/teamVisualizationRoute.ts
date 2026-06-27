import { isOpenJiuwenTeamRuntime, type TeamState } from './teamStatus';

export type TeamVisualizationLayout = 'agent-grid' | 'stepper' | 'specialized' | 'delegation';

/** Pick team viz layout from pattern + collaboration (ADR-013, no per-expert overrides). */
export function resolveTeamVisualizationLayout(team: TeamState): TeamVisualizationLayout {
  if (team.visualizationMode === 'delegation' || isOpenJiuwenTeamRuntime(team.teamRuntime)) {
    return 'delegation';
  }
  switch (team.pattern) {
    case 'generator-verifier':
    case 'message-bus':
    case 'shared-state':
      return 'specialized';
    case 'agent-team':
      return 'agent-grid';
    case 'orchestrator':
    case 'pipeline':
      return team.collaboration === 'parallel' ? 'agent-grid' : 'stepper';
    default:
      return team.collaboration === 'parallel' ? 'agent-grid' : 'stepper';
  }
}
