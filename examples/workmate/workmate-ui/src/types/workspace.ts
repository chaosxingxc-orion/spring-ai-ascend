import type { PermissionMode, ModelEffort } from '../types/api';

export interface WorkspacePreset {
  id: string;
  name: string;
  path: string;
  description: string;
}

export interface CreateSessionOptions {
  title?: string;
  expertId?: string;
  permissionMode?: PermissionMode;
  gitBranch?: string;
  workspacePath?: string;
  modelId?: string;
  effort?: ModelEffort;
  autoArchive?: boolean;
  enabledConnectorIds?: string[];
  enabledSkillIds?: string[];
}
