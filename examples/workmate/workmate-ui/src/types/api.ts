export type SessionStatus = 'CREATED' | 'RUNNING' | 'STOPPED' | 'COMPLETED';

export type PermissionMode = 'ASK' | 'PLAN' | 'CRAFT';

export type ModelEffort = 'AUTO' | 'MINIMAL' | 'LOW' | 'MEDIUM' | 'HIGH' | 'MAX';

export interface ModelOption {
  id: string;
  displayName: string;
  provider: string;
  modelName: string;
  capabilities: string[];
}

export interface ModelEffortOption {
  id: ModelEffort;
  label: string;
}

export interface ModelCatalog {
  defaultModelId: string;
  models: ModelOption[];
  efforts: ModelEffortOption[];
}

export interface Session {
  id: string;
  title: string;
  workspaceRoot: string;
  workspaceKey?: string;
  status: SessionStatus;
  expertId?: string | null;
  permissionMode: PermissionMode;
  permissionModeBeforePlan?: PermissionMode | null;
  modelId?: string | null;
  effort?: ModelEffort | null;
  promptTokens?: number;
  completionTokens?: number;
  pinned?: boolean;
  archivedAt?: string | null;
  enabledConnectorIds?: string[];
  enabledSkillIds?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface SessionLimits {
  activeCount: number;
  maxActive: number;
  autoArchiveOnCreate?: boolean;
  archivableCount?: number;
}

export interface AutoArchivedSession {
  id: string;
  title: string;
  archivedAt: string;
}

export interface AutoArchiveResponse {
  archived: AutoArchivedSession[];
  activeCount: number;
  maxActive: number;
}

export interface CreateSessionResult extends Session {
  autoArchived?: AutoArchivedSession[];
}

export interface MyFile {
  sessionId: string;
  sessionTitle: string;
  path: string;
  name: string;
  mime: string;
  size: number;
  updatedAt: string;
  favorite: boolean;
}

export type MyFilesSort = 'name' | 'updatedAt';
export type MyFilesOrder = 'asc' | 'desc';

export type I18nMap = Record<string, string>;

export interface ExpertMember {
  id: string;
  name: string;
  expertId: string;
  role?: string;
  order?: number;
  avatar?: string;
  participantRole?: string;
  profession?: I18nMap;
  nickname?: string;
}

export interface ExpertLead {
  name?: string;
  title?: string | I18nMap;
  avatar?: string;
}

export interface ExpertCoordination {
  pattern?: string;
  acceptanceCriteria?: string | null;
  termination?: {
    maxIterations?: number;
    timeBudgetMs?: number;
    convergence?: string;
    decider?: string;
  } | null;
}

export interface Expert {
  id: string;
  name: string;
  description: string;
  expertType: string;
  defaultInitPrompt?: string | null;
  category?: string | null;
  tags: string[];
  skillCompatibility: string[];
  preloadSkills?: string[];
  members?: ExpertMember[];
  collaboration?: string | null;
  lead?: ExpertLead | null;
  coordination?: ExpertCoordination | null;
  uiLabels?: Record<string, string>;
  displayName?: I18nMap;
  profession?: I18nMap;
  maxTurns?: number | null;
  quickPrompts?: string[];
  /** Optional lifecycle flag from API or `beta` tag. */
  beta?: boolean | null;
  /** Team runtime kind from expert.yaml, e.g. openjiuwen-team */
  teamRuntime?: string | null;
}

export interface PendingApproval {
  approvalId: string;
  sessionId: string;
  taskId: string;
  toolName: string;
  riskLevel: string;
  reason: string;
  summary: string;
  args: Record<string, unknown>;
  createdAt: string;
}

export type ApprovalDecision = 'approve' | 'deny';
export type ApprovalDecisionScope = 'ONCE' | 'SESSION';

export interface Artifact {
  path: string;
  name: string;
  mime: string;
  size: number;
  updatedAt: string;
}

export interface FileVersion {
  seq: number;
  path: string;
  op: 'created' | 'modified' | 'deleted';
  versionId: string;
  ts: string;
  runId: string;
  bytes: number;
  sha256: string;
}

export interface FileChange {
  path: string;
  op: 'created' | 'modified' | 'deleted';
  ts: string;
  runId: string;
  bytes: number;
  seq: number;
}

export interface FileDiff {
  path: string;
  mime: string;
  original: string;
  modified: string;
  truncated: boolean;
}

export interface FileContent {
  path: string;
  mime: string;
  content: string;
  size: number;
  truncated: boolean;
}

export type WorkspaceEntryType = 'file' | 'dir';

export interface WorkspaceEntry {
  name: string;
  path: string;
  type: WorkspaceEntryType;
  size?: number;
  mime?: string;
  updatedAt?: string;
}

export interface AuditEntry {
  seqGlobal: number;
  runEventId: string;
  sessionId: string;
  runId: string;
  eventName: string;
  payloadHash: string;
  prevHash: string;
  entryHash: string;
  category: string;
  decision: string;
  createdAt: string;
}

export interface AuditEntryPage {
  entries: AuditEntry[];
  nextCursor: number | null;
}

export interface AuditVerifyResult {
  ok: boolean;
  verifiedThroughSeq: number;
  brokenSeqGlobal?: number | null;
  field?: string | null;
  expected?: string | null;
  actual?: string | null;
}

export interface MemoryStatus {
  enabled: boolean;
  autoCapture: boolean;
  ownerId: string;
  content: string;
  injectPreview: string;
  hasContent: boolean;
  charCount: number;
}

export interface MemoryCaptureResult {
  status: string;
  entries: string[];
  reason?: string | null;
}

export interface TeamSnapshotMember {
  memberId: string;
  name: string;
  expertId: string;
  role?: string | null;
  order: number;
  avatar?: string | null;
  promptSummary?: string;
  backendType: string;
  subscriptions: string[];
  /** idle | running | completed | error — from run_events enrichment */
  status?: string | null;
}

export interface TeamSnapshot {
  teamId: string;
  teamName: string;
  description: string;
  pattern?: string | null;
  collaboration?: string | null;
  teamPromptSummary?: string;
  lead?: { name?: string; title?: string; avatar?: string } | null;
  members: TeamSnapshotMember[];
  source: string;
}
