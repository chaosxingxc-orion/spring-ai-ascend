import type { InputDockConfig } from '../input-dock/types';
import { isTeamMemberFollowUpAvailable } from '../../lib/teamStatus';
import { workspaceLabelForSession } from '../../lib/sessionWorkspace';
import type { Expert, ModelCatalog, ModelEffort, PermissionMode, Session } from '../../types/api';
import type { ConnectorInfo, SkillInfo } from '../../types/market';
import type { WorkspacePreset } from '../../types/workspace';
import type { GitSelection } from '../../components/GitTaskStarterPanel';
import type { TeamState } from '../../lib/teamStatus';
import type { SendMessageExtras } from '../../types/sendMessage';

export interface BuildInputDockConfigParams {
  experts: Expert[];
  selectedExpertId: string;
  permissionMode: PermissionMode;
  sessionLocked: boolean;
  activeStreaming: boolean;
  creatingSession: boolean;
  hasPendingQuestion: boolean;
  isNewTask: boolean;
  draftEnabledConnectorIds: string[];
  draftEnabledSkillIds: string[];
  activeSession: Session | null;
  marketSkills: SkillInfo[];
  marketConnectors: ConnectorInfo[];
  workspacePresets: WorkspacePreset[];
  selectedWorkspacePath: string;
  gitSelection: GitSelection | null;
  activeId: string | null;
  modelCatalog: ModelCatalog | null;
  dockModelId: string;
  dockEffort: ModelEffort;
  sessionDraftSeed: string;
  activeTeam: TeamState | null;
  welcomePlaceholderNew: string;
  welcomePlaceholderSession: string;
  onExpertChange: (expertId: string) => void;
  onPermissionModeChange: (mode: PermissionMode) => void;
  onOpenMarket: InputDockConfig['onOpenMarket'];
  onExpertSummon: (expert: Expert) => void;
  onEnabledConnectorIdsChange: (ids: string[]) => void;
  onEnabledSkillIdsChange: (ids: string[]) => void;
  onMarketSkillsChange: (skills: SkillInfo[]) => void;
  onMarketConnectorsChange: (connectors: ConnectorInfo[]) => void;
  onWorkspacePathChange: (path: string) => void;
  onGitSelectionChange: (selection: GitSelection | null) => void;
  onModelChange: (modelId: string) => void;
  onEffortChange: (effort: ModelEffort) => void;
  onSend: (message: string, extras?: SendMessageExtras) => void;
  onStop: () => void;
}

export function buildInputDockConfig(params: BuildInputDockConfigParams): InputDockConfig {
  const {
    experts,
    selectedExpertId,
    permissionMode,
    sessionLocked,
    activeStreaming,
    creatingSession,
    hasPendingQuestion,
    isNewTask,
    draftEnabledConnectorIds,
    draftEnabledSkillIds,
    activeSession,
    marketSkills,
    marketConnectors,
    workspacePresets,
    selectedWorkspacePath,
    gitSelection,
    activeId,
    modelCatalog,
    dockModelId,
    dockEffort,
    sessionDraftSeed,
    activeTeam,
    welcomePlaceholderNew,
    welcomePlaceholderSession,
    onExpertChange,
    onPermissionModeChange,
    onOpenMarket,
    onExpertSummon,
    onEnabledConnectorIdsChange,
    onEnabledSkillIdsChange,
    onMarketSkillsChange,
    onMarketConnectorsChange,
    onWorkspacePathChange,
    onGitSelectionChange,
    onModelChange,
    onEffortChange,
    onSend,
    onStop,
  } = params;

  return {
    experts,
    selectedExpertId,
    permissionMode,
    sessionLocked,
    disabled: activeStreaming || creatingSession || hasPendingQuestion,
    streaming: activeStreaming,
    onExpertChange,
    onPermissionModeChange,
    onOpenMarket,
    onExpertSummon,
    enabledConnectorIds: isNewTask
      ? draftEnabledConnectorIds
      : (activeSession?.enabledConnectorIds ?? []),
    onEnabledConnectorIdsChange,
    enabledSkillIds: isNewTask
      ? draftEnabledSkillIds
      : (activeSession?.enabledSkillIds ?? []),
    onEnabledSkillIdsChange,
    marketSkills,
    marketConnectors,
    onMarketSkillsChange,
    onMarketConnectorsChange,
    workspacePresets,
    selectedWorkspacePath,
    gitBranch: gitSelection?.branch,
    gitLabel: gitSelection?.label,
    workspaceReadOnlyLabel: activeSession
      ? workspaceLabelForSession(activeSession, workspacePresets)
      : undefined,
    onWorkspacePathChange,
    onGitSelectionChange,
    sessionId: activeId,
    modelCatalog,
    modelId: dockModelId,
    effort: dockEffort,
    onModelChange,
    onEffortChange,
    draftSeed: !isNewTask && sessionDraftSeed ? sessionDraftSeed : undefined,
    onSend,
    onStop,
    placeholder: isNewTask
      ? welcomePlaceholderNew
      : (isTeamMemberFollowUpAvailable(activeTeam)
        ? '团队已完成 — 输入 @成员 续聊，由主理人重新派活'
        : welcomePlaceholderSession),
  };
}
