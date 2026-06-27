import type { Expert, PermissionMode, ModelCatalog, ModelEffort } from '../../types/api';
import type { ConnectorInfo, SkillInfo } from '../../types/market';
import type { SendMessageExtras } from '../../types/sendMessage';
import type { WorkspacePreset } from '../../types/workspace';
import type { GitSelection } from '../../components/GitTaskStarterPanel';

import type { ExpertMarketKind } from '../../lib/expertMarketFilter';

/** Input Dock 配置 — 新建页与会话页共用 */
export interface InputDockConfig {
  experts: Expert[];
  selectedExpertId: string;
  permissionMode: PermissionMode;
  sessionLocked: boolean;
  placeholder?: string;
  disabled: boolean;
  streaming: boolean;
  centered?: boolean;
  workspacePresets?: WorkspacePreset[];
  selectedWorkspacePath?: string;
  gitBranch?: string;
  gitLabel?: string;
  workspaceReadOnlyLabel?: string;
  onExpertChange: (expertId: string) => void;
  onPermissionModeChange: (mode: PermissionMode) => void;
  onOpenMarket?: (tab: 'experts' | 'skills' | 'connectors', kind?: ExpertMarketKind) => void;
  onExpertSummon?: (expert: Expert) => void;
  enabledConnectorIds?: string[];
  onEnabledConnectorIdsChange?: (connectorIds: string[]) => void;
  enabledSkillIds?: string[];
  onEnabledSkillIdsChange?: (skillIds: string[]) => void;
  marketSkills?: SkillInfo[];
  marketConnectors?: ConnectorInfo[];
  onMarketSkillsChange?: (skills: SkillInfo[]) => void;
  onMarketConnectorsChange?: (connectors: ConnectorInfo[]) => void;
  onWorkspacePathChange?: (path: string) => void;
  onGitSelectionChange?: (selection: GitSelection | null) => void;
  draftSeed?: string;
  sessionId?: string | null;
  modelCatalog?: ModelCatalog | null;
  modelId?: string;
  effort?: ModelEffort;
  onModelChange?: (modelId: string) => void;
  onEffortChange?: (effort: ModelEffort) => void;
  onSend: (message: string, extras?: SendMessageExtras) => void;
  onStop: () => void;
}
