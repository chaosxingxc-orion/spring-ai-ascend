import type { Expert } from './api';

export type OfficeAssetSource = 'BUILTIN' | 'MARKET' | 'IMPORT' | 'DRAFT';

export interface StudioExpertListItem {
  summary: Expert;
  source: OfficeAssetSource;
  sourceDir: string;
  promptFile: string;
}

export interface StudioExpertSource {
  summary: Expert;
  promptFile: string;
  promptContent: string;
  expertYaml: string;
  source: OfficeAssetSource;
  sourceDir: string;
}

export interface StudioExpertWriteBody {
  id?: string;
  name: string;
  description: string;
  expertType?: string;
  promptContent: string;
  promptFile?: string;
  defaultInitPrompt?: string;
  category?: string;
  tags?: string[];
  skillCompatibility?: string[];
  preloadSkills?: string[];
  quickPrompts?: string[];
  maxTurns?: number | null;
  displayName?: Record<string, string>;
  profession?: Record<string, string>;
}

export interface StudioExpertCapabilityItem {
  id: string;
  name: string;
  description: string | null;
  found: boolean;
  source: string | null;
}

export interface StudioExpertCapabilities {
  skills: StudioExpertCapabilityItem[];
  connectors: StudioExpertCapabilityItem[];
  unresolved: StudioExpertCapabilityItem[];
}

export interface StudioSkillSummary {
  id: string;
  name: string;
  description: string;
  category: string;
  tags: string[];
  source: string;
  installed: boolean;
}

export interface StudioSkillListItem {
  summary: StudioSkillSummary;
  source: OfficeAssetSource;
  sourceDir: string;
  skillFile: string;
}

export interface StudioSkillSource {
  summary: StudioSkillSummary;
  skillFile: string;
  skillContent: string;
  skillYaml: string;
  source: OfficeAssetSource;
  sourceDir: string;
}

export interface StudioSkillFileEntry {
  path: string;
  sizeBytes: number;
  textReadable: boolean;
}

export interface StudioSkillFileContent {
  path: string;
  content: string;
  truncated: boolean;
  binary: boolean;
  editable: boolean;
}

export interface StudioSkillWriteBody {
  id?: string;
  name: string;
  description: string;
  category?: string;
  tags?: string[];
  skillContent: string;
  skillFile?: string;
  source?: string;
  defaultInstalled?: boolean;
}

export interface StudioDryRunResult {
  sessionId: string;
  expertId: string;
  title: string;
}

export interface StudioReloadResult {
  experts: number;
  skills: number;
  warnings: string[];
}

export interface StudioValidationResult {
  valid: boolean;
  message: string;
}

export interface StudioAssetDiff {
  hasDraft: boolean;
  hasBaseline: boolean;
  canRollback: boolean;
  currentSource: OfficeAssetSource;
  baselineSource: OfficeAssetSource | null;
  promptFile: string;
  baselinePrompt: string | null;
  draftPrompt: string;
  changedFields: string[];
}

export interface StudioExportItem {
  id: string;
  assetType: string;
  name: string;
  suggestedOfficePath: string;
  files: string[];
}

export interface StudioExportPreview {
  items: StudioExportItem[];
  expertCount: number;
  skillCount: number;
}

export interface StudioWelcomeSource {
  welcomeYaml: string;
  baselineYaml: string;
  source: OfficeAssetSource;
  sourcePath: string;
}

export interface StudioWelcomeWriteBody {
  welcomeYaml: string;
}

export interface StudioPlaybookListItem {
  id: string;
  title: string;
  description: string;
  placements: string[];
  source: OfficeAssetSource;
  sourcePath: string;
}

export interface StudioPlaybookSource {
  id: string;
  title: string;
  description: string;
  accent: string;
  expertId: string;
  initPrompt: string;
  placements: string[];
  playbookYaml: string;
  source: OfficeAssetSource;
  sourcePath: string;
}

export interface StudioPlaybookWriteBody {
  id?: string;
  title: string;
  description?: string;
  accent?: string;
  expertId?: string;
  initPrompt: string;
  placements?: string[];
}

export interface StudioConfig {
  enabled: boolean;
  auditEnabled: boolean;
}

export interface StudioDraftMeta {
  assetType: string;
  assetId: string;
  status: 'draft' | 'published';
  origin: string;
  updatedAt: string | null;
}

export interface StudioRuntimeOverview {
  models: {
    defaultModelId: string;
    models: Array<{ id: string; displayName: string; provider?: string; modelName?: string; capabilities?: string[] }>;
    efforts: Array<{ id: string; label: string }>;
  };
  mcpServers: Array<{ serverId: string; connected: boolean; toolCount: number; invalidSchemaCount: number; toolsLimitWarning: boolean; lastError?: string | null }>;
  mcpTools: Array<{ serverId: string; toolName: string; description?: string; openJiuwenToolId: string }>;
  connectors: Array<{ id: string; name: string; description?: string; status: string; toolCount: number; runnable: boolean; source?: string }>;
}

export interface StudioTeamMemberWriteBody {
  id: string;
  name: string;
  expertId?: string;
  role?: string;
  order?: number;
  avatar?: string;
  profession?: Record<string, string>;
  backend?: string;
  promptContent?: string;
  expertYaml?: string;
}

export interface StudioCoordinationWriteBody {
  pattern: string;
  termination?: {
    maxIterations?: number;
    timeBudgetMs?: number;
    convergence?: string;
    decider?: string;
  };
  acceptanceCriteria?: string;
}

export interface StudioTeamWriteBody {
  id?: string;
  name: string;
  description: string;
  promptContent: string;
  defaultInitPrompt?: string;
  category?: string;
  tags?: string[];
  collaboration?: string;
  teamRuntime?: string;
  coordination?: StudioCoordinationWriteBody;
  lead?: { name: string; title?: Record<string, string>; avatar?: string };
  teamAgent?: { teamMode?: string; spawnMode?: string; teammateMode?: string };
  members: StudioTeamMemberWriteBody[];
  maxTurns?: number | null;
}

export interface StudioTeamMemberView {
  member: {
    id: string;
    name: string;
    expertId: string;
    role?: string;
    order?: number;
    avatar?: string;
    profession?: Record<string, string>;
  };
  expertResolved: boolean;
  expertSource: string;
  promptFile?: string;
  promptContent?: string;
  expertYaml?: string;
}

export interface StudioRuntimePreview {
  requestedRuntime?: string | null;
  resolvedRuntime: string;
  coordinationPattern: string;
  migratablePattern: boolean;
  hasLead: boolean;
  hint: string;
}

export interface StudioTeamView {
  team: StudioExpertSource;
  members: StudioTeamMemberView[];
  runtimePreview: StudioRuntimePreview;
  warnings: string[];
}
