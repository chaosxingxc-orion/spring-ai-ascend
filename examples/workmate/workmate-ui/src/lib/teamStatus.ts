import type { TeamSnapshot } from '../types/api';
import type { TeamMember, TeamMemberStatus } from '../components/TeamStatusRow';
import type { Expert } from '../types/api';
import { memberDisplayLabel, resolveI18n, resolveLeadTitle } from './teamUiLabels';
import type {
  CoordinationPattern,
  TeamCollaboration,
  TeamMemberEventPayload,
  TeamStartedPayload,
  TeamIterationPayload,
  TeamVerifyPayload,
} from '../types/events';

export type TeamLeadStatus = 'idle' | 'synthesizing' | 'done';
export type TeamPhase = 'idle' | 'running' | 'synthesizing' | 'done';
export type GeneratorVerifierPhase = 'idle' | 'generating' | 'verifying' | 'accepted' | 'rejected';

const LEADER_PATTERNS: CoordinationPattern[] = ['orchestrator', 'agent-team'];

export function patternHasLead(pattern: CoordinationPattern): boolean {
  return LEADER_PATTERNS.includes(pattern);
}

function normalizePattern(
  pattern: string | null | undefined,
  collaboration: string | null | undefined,
): CoordinationPattern {
  const known: CoordinationPattern[] = [
    'orchestrator',
    'pipeline',
    'agent-team',
    'generator-verifier',
    'message-bus',
    'shared-state',
  ];
  if (pattern && known.includes(pattern as CoordinationPattern)) {
    return pattern as CoordinationPattern;
  }
  // 向后兼容：无 pattern 时由 collaboration 推导（ADR-013）。
  return collaboration === 'parallel' ? 'agent-team' : 'orchestrator';
}

export interface TeamLeadState {
  name: string;
  title?: string;
  avatar?: string;
  status: TeamLeadStatus;
}

/** generator-verifier 单轮审计记录（可回看的协作过程档案）。 */
export interface GeneratorVerifierRound {
  iteration: number;
  /** 生成者本轮草稿摘要 */
  generateSummary?: string;
  /** 校验结论 */
  verdict?: 'accepted' | 'rejected';
  /** 驳回反馈 */
  feedback?: string;
  /** 是否为可编程门禁（非 LLM）驳回 */
  programmatic?: boolean;
}

/** 团队共享黑板条目（W27 interim，来自 team.memory 事件）。 */
export interface TeamMemoryEntry {
  section: string;
  preview?: string;
  action?: string;
  totalBytes?: number;
  version?: number;
}

export type TeamBusPublishSource = 'orchestrator' | 'mid-run' | 'outcome';

/** W26 interim：总线泳道条目（team.bus.published）。 */
export interface TeamBusEntry {
  topic: string;
  authorMemberId?: string;
  authorName?: string;
  preview?: string;
  publishSource?: TeamBusPublishSource;
}

export type TeamVisualizationMode = 'pipeline' | 'delegation';

export interface TeamState {
  pattern: CoordinationPattern;
  collaboration: TeamCollaboration;
  /** openjiuwen-team vs workmate orchestrator */
  teamRuntime?: string;
  /** UI layout selector for TeamAgent delegation model */
  visualizationMode?: TeamVisualizationMode;
  /** Members currently executing via send_message */
  activeMemberIds?: string[];
  /** Recently finished members (delegation bar ✓ flash, max 2) */
  recentlyCompletedMemberIds?: string[];
  /** build_team result snapshot */
  teamBuild?: { teamName?: string; displayName?: string };
  lead: TeamLeadState | null;
  members: TeamMember[];
  phase: TeamPhase;
  anyMemberFailed: boolean;
  /** W27 interim：workspace 黑板路径 */
  blackboardPath?: string;
  /** W27 interim：team.memory 累积条目 */
  memoryEntries?: TeamMemoryEntry[];
  /** W26：message-bus 泳道 */
  busLanes?: Record<string, TeamBusEntry[]>;
  busEntryCount?: number;
  busMode?: string;
  topicBusProvider?: string;
  busSubscriptions?: Array<{ subscriberMemberId: string; topics: string[] }>;
  /** W26.1：多轮 message-bus 进度 */
  bus?: {
    iteration: number;
    maxIterations: number;
    convergenceStreak?: number;
    convergenceTarget?: number;
    converged?: boolean;
  };
  /** shared-state 拓扑（W27） */
  ss?: {
    iteration: number;
    maxIterations: number;
    convergenceStreak: number;
    convergenceTarget: number;
    blackboardVersion?: number;
    converged?: boolean;
    roundHadNewFindings?: boolean;
  };
  /** generator-verifier 拓扑专用 */
  gv?: {
    iteration: number;
    maxIterations: number;
    phase: GeneratorVerifierPhase;
    lastFeedback?: string;
    generatorId?: string;
    verifierId?: string;
    /** 逐轮审计日志（可累积、可回看），从 run_events 重放可重建。 */
    rounds: GeneratorVerifierRound[];
  };
}

/** 在审计日志中定位/创建指定轮次，返回新数组（不可变更新）。 */
function upsertRound(
  rounds: GeneratorVerifierRound[] | undefined,
  iteration: number,
  patch: Partial<GeneratorVerifierRound>,
): GeneratorVerifierRound[] {
  const list = rounds ? [...rounds] : [];
  const idx = list.findIndex((r) => r.iteration === iteration);
  if (idx < 0) {
    list.push({ iteration, ...patch });
  } else {
    list[idx] = { ...list[idx], ...patch };
  }
  return list.sort((a, b) => a.iteration - b.iteration);
}

const DEFAULT_LEAD_TITLE = '团队负责人';

export function isOpenJiuwenTeamRuntime(teamRuntime: string | null | undefined): boolean {
  return teamRuntime === 'openjiuwen-team';
}

/** Ensure openjiuwen TeamAgent sessions carry delegation viz + phase scaffold before replay. */
export function ensureDelegationVisualization(
  team: TeamState,
  expertTeamRuntime?: string | null,
): TeamState {
  const teamRuntime = team.teamRuntime ?? expertTeamRuntime ?? undefined;
  if (!isOpenJiuwenTeamRuntime(teamRuntime) && team.visualizationMode !== 'delegation') {
    return team;
  }
  return {
    ...team,
    teamRuntime,
    visualizationMode: 'delegation',
  };
}

export function isDelegationTeamState(
  team: TeamState | null | undefined,
  expertTeamRuntime?: string | null,
): boolean {
  if (!team) {
    return isOpenJiuwenTeamRuntime(expertTeamRuntime);
  }
  return (
    team.visualizationMode === 'delegation'
    || isOpenJiuwenTeamRuntime(team.teamRuntime)
    || isOpenJiuwenTeamRuntime(expertTeamRuntime)
  );
}

/** Whether session should use reference-style delegation bar. */
export function isTeamDelegationSession(
  team: TeamState | null | undefined,
  expertTeamRuntime?: string | null,
): boolean {
  if (team?.visualizationMode === 'delegation') {
    return true;
  }
  return isOpenJiuwenTeamRuntime(team?.teamRuntime ?? expertTeamRuntime);
}

export function shouldShowDelegationDock(
  team: TeamState | null | undefined,
  expertTeamRuntime?: string | null,
): boolean {
  if (!team) {
    return false;
  }
  // 完成后（phase==='done'）仍保留委派进度条，展示各成员/阶段的最终状态，避免任务"消失"。
  return isTeamDelegationSession(team, expertTeamRuntime);
}

/** User @member bypass is only meaningful while the team runtime is still live. */
export function isTeamBypassMessagingAvailable(team: TeamState | null | undefined): boolean {
  if (!team?.teamBuild) {
    return false;
  }
  return team.phase === 'running' || team.phase === 'synthesizing';
}

/** After team completion: continue via main input @mention (new leader delegation), not mailbox bypass. */
export function isTeamMemberFollowUpAvailable(team: TeamState | null | undefined): boolean {
  return Boolean(team?.teamBuild && team.phase === 'done');
}

function delegationModeFromRuntime(teamRuntime: string | null | undefined): TeamVisualizationMode | undefined {
  return isOpenJiuwenTeamRuntime(teamRuntime) ? 'delegation' : 'pipeline';
}

/** 召唤前 / 运行前从 descriptor 预置团队（成员待命）。 */
export function initialTeamState(expert: Expert | null | undefined): TeamState | null {
  if (!expert || expert.expertType !== 'team') {
    return null;
  }
  const members: TeamMember[] = (expert.members && expert.members.length > 0)
    ? expert.members.map((member, index) => ({
        id: member.id,
        name: memberDisplayLabel(expert, member.id) ?? member.name,
        status: 'idle' as const,
        role: member.role,
        profession: resolveI18n(member.profession),
        order: member.order ?? index + 1,
        avatar: member.avatar,
      }))
    : (expert.tags.length > 0 ? expert.tags : [expert.name]).map((name, index) => ({
        id: `${expert.id}-${index}`,
        name,
        status: 'idle' as const,
        order: index + 1,
      }));
  const pattern = normalizePattern(expert.coordination?.pattern, expert.collaboration);
  const hasLead = patternHasLead(pattern);
  const teamRuntime = expert.teamRuntime ?? undefined;
  const visualizationMode = delegationModeFromRuntime(teamRuntime);
  const seededMembers =
    visualizationMode === 'delegation'
      ? members.map((member) => ({ ...member, status: 'not-scheduled' as const }))
      : members;
  return {
    pattern,
    collaboration: (expert.collaboration as TeamCollaboration) ?? 'sequential',
    teamRuntime,
    visualizationMode,
    activeMemberIds: visualizationMode === 'delegation' ? [] : undefined,
    recentlyCompletedMemberIds: visualizationMode === 'delegation' ? [] : undefined,
    lead: hasLead
      ? {
          name: expert.lead?.name ?? `${expert.name} 团长`,
          title: resolveLeadTitle(expert.lead?.title) || DEFAULT_LEAD_TITLE,
          avatar: expert.lead?.avatar,
          status: 'idle',
        }
      : null,
    members: sortByOrder(seededMembers),
    phase: 'idle',
    anyMemberFailed: false,
    gv:
      pattern === 'generator-verifier'
        ? {
            iteration: 0,
            maxIterations: expert.coordination?.termination?.maxIterations ?? 3,
            phase: 'idle',
            generatorId: expert.members?.find((m) => m.participantRole === 'generator')?.id
              ?? seededMembers[0]?.id,
            verifierId: expert.members?.find((m) => m.participantRole === 'verifier')?.id
              ?? seededMembers[1]?.id,
            rounds: [],
          }
        : undefined,
    ss:
      pattern === 'shared-state'
        ? {
            iteration: 0,
            maxIterations: expert.coordination?.termination?.maxIterations ?? 4,
            convergenceStreak: 0,
            convergenceTarget: parseConvergenceTarget(expert.coordination?.termination?.convergence),
          }
        : undefined,
  };
}

function parseConvergenceTarget(convergence: string | undefined): number {
  if (!convergence) {
    return 0;
  }
  const match = convergence.match(/noNewFindingsForN\((\d+)\)/);
  if (!match) {
    return 0;
  }
  const n = parseInt(match[1], 10);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** team.started — 用服务端 roster 快照重建（刷新/续传后无需查 descriptor）。 */
export function applyTeamStarted(
  prev: TeamState | null,
  payload: TeamStartedPayload,
): TeamState {
  const rosterMembers: TeamMember[] = (payload.members ?? []).map((entry, index) => ({
    id: entry.memberId,
    name: entry.memberName ?? entry.memberId,
    status: 'idle' as const,
    role: entry.role,
    order: entry.order ?? index + 1,
    avatar: entry.avatar,
  }));
  const members = rosterMembers.length > 0 ? sortByOrder(rosterMembers) : (prev?.members ?? []);
  const pattern = normalizePattern(payload.pattern, payload.collaboration ?? prev?.collaboration);
  const teamRuntime = payload.teamRuntime ?? prev?.teamRuntime;
  const visualizationMode: TeamVisualizationMode | undefined =
    teamRuntime === 'openjiuwen-team'
      ? 'delegation'
      : prev?.visualizationMode ?? delegationModeFromRuntime(teamRuntime) ?? 'pipeline';
  const seededMembers = visualizationMode === 'delegation'
    ? members.map((member) => ({ ...member, status: 'not-scheduled' as const }))
    : members;
  const hasLead = patternHasLead(pattern);
  const leadFromPayload = payload.lead ?? prev?.lead;
  return {
    pattern,
    collaboration: payload.collaboration ?? prev?.collaboration ?? 'sequential',
    teamRuntime,
    visualizationMode,
    activeMemberIds: visualizationMode === 'delegation' ? [] : prev?.activeMemberIds,
    recentlyCompletedMemberIds: visualizationMode === 'delegation' ? [] : prev?.recentlyCompletedMemberIds,
    lead:
      hasLead && leadFromPayload
        ? {
            name: leadFromPayload.name ?? '团长',
            title: leadFromPayload.title ?? DEFAULT_LEAD_TITLE,
            avatar: leadFromPayload.avatar,
            status: 'idle',
          }
        : null,
    members: seededMembers,
    phase: 'running',
    anyMemberFailed: false,
    gv:
      pattern === 'generator-verifier'
        ? {
            iteration: 0,
            maxIterations: payload.maxIterations ?? prev?.gv?.maxIterations ?? 3,
            phase: 'idle',
            generatorId:
              payload.members?.find((e) => e.participantRole === 'generator')?.memberId
              ?? seededMembers[0]?.id,
            verifierId:
              payload.members?.find((e) => e.participantRole === 'verifier')?.memberId
              ?? seededMembers[1]?.id,
            rounds: [],
          }
        : prev?.gv,
    ss:
      pattern === 'shared-state'
        ? {
            iteration: 0,
            maxIterations: payload.maxIterations ?? prev?.ss?.maxIterations ?? 4,
            convergenceStreak: 0,
            convergenceTarget:
              payload.convergenceTarget ?? prev?.ss?.convergenceTarget ?? 0,
          }
        : prev?.ss,
    busMode:
      pattern === 'message-bus'
        ? (payload.busMode ?? prev?.busMode ?? 'async-subscribe')
        : prev?.busMode,
    topicBusProvider:
      pattern === 'message-bus'
        ? (payload.topicBusProvider ?? prev?.topicBusProvider)
        : prev?.topicBusProvider,
    bus:
      pattern === 'message-bus'
        ? {
            iteration: 0,
            maxIterations: payload.maxIterations ?? prev?.bus?.maxIterations ?? 1,
            convergenceStreak: 0,
            convergenceTarget: payload.convergenceTarget ?? prev?.bus?.convergenceTarget ?? 0,
          }
        : prev?.bus,
    // Preserve build state across leader re-runs (e.g. HITL answer → new parentRunId).
    teamBuild: prev?.teamBuild,
  };
}

export function applyTeamMemory(
  prev: TeamState | null,
  payload: {
    path: string;
    section: string;
    preview?: string;
    totalBytes?: number;
    action?: string;
    version?: number;
  },
): TeamState {
  const base: TeamState = prev ?? {
    pattern: 'orchestrator',
    collaboration: 'sequential',
    lead: null,
    members: [],
    phase: 'running',
    anyMemberFailed: false,
  };
  const entry: TeamMemoryEntry = {
    section: payload.section,
    preview: payload.preview,
    action: payload.action,
    totalBytes: payload.totalBytes,
    version: payload.version,
  };
  const ss =
    base.ss && payload.version != null
      ? { ...base.ss, blackboardVersion: payload.version }
      : base.ss;
  return {
    ...base,
    blackboardPath: payload.path,
    memoryEntries: [...(base.memoryEntries ?? []), entry],
    ss,
  };
}

export function applyBusSubscribed(
  prev: TeamState | null,
  payload: { subscriberMemberId: string; topics: string[] },
): TeamState {
  const base: TeamState = prev ?? {
    pattern: 'message-bus',
    collaboration: 'parallel',
    lead: null,
    members: [],
    phase: 'running',
    anyMemberFailed: false,
  };
  const subs = [...(base.busSubscriptions ?? []), payload];
  return { ...base, busSubscriptions: subs, busMode: base.busMode ?? 'async-subscribe' };
}

export function applyBusPublished(
  prev: TeamState | null,
  payload: {
    topic: string;
    authorMemberId?: string;
    authorMemberName?: string;
    preview?: string;
    publishSource?: TeamBusPublishSource;
  },
): TeamState {
  const base: TeamState = prev ?? {
    pattern: 'message-bus',
    collaboration: 'parallel',
    lead: null,
    members: [],
    phase: 'running',
    anyMemberFailed: false,
  };
  const topic = payload.topic || 'bus';
  const entry: TeamBusEntry = {
    topic,
    authorMemberId: payload.authorMemberId,
    authorName: payload.authorMemberName,
    preview: payload.preview,
    publishSource: payload.publishSource,
  };
  const lanes = { ...(base.busLanes ?? {}) };
  lanes[topic] = [...(lanes[topic] ?? []), entry];
  const count = Object.values(lanes).reduce((sum, list) => sum + list.length, 0);
  return {
    ...base,
    busLanes: lanes,
    busEntryCount: count,
  };
}

export function applyTeamBuildCompleted(
  prev: TeamState | null,
  payload: { teamName?: string; displayName?: string },
): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    teamBuild: {
      teamName: payload.teamName ?? prev.teamBuild?.teamName,
      displayName: payload.displayName ?? prev.teamBuild?.displayName,
    },
  };
}

export function applyMemberEvent(
  prev: TeamState | null,
  event: 'started' | 'reawakened' | 'completed' | 'paused' | 'failed',
  payload: TeamMemberEventPayload,
  seq?: number,
): TeamState {
  const base: TeamState = prev ?? {
    pattern: 'orchestrator',
    collaboration: 'sequential',
    lead: null,
    members: [],
    phase: 'running',
    anyMemberFailed: false,
  };
  const isDelegation = base.visualizationMode === 'delegation';
  let activeMemberIds = [...(base.activeMemberIds ?? [])];
  let recentlyCompletedMemberIds = [...(base.recentlyCompletedMemberIds ?? [])];
  const status: TeamMemberStatus =
    event === 'started' || event === 'reawakened'
      ? 'running'
      : event === 'completed'
        ? 'completed'
        : event === 'paused'
          ? 'paused'
        : 'error';
  if (event === 'started' || event === 'reawakened') {
    if (!activeMemberIds.includes(payload.memberId)) {
      activeMemberIds = [...activeMemberIds, payload.memberId];
    }
    recentlyCompletedMemberIds = recentlyCompletedMemberIds.filter((id) => id !== payload.memberId);
  } else {
    activeMemberIds = activeMemberIds.filter((id) => id !== payload.memberId);
    if ((event === 'completed' || event === 'paused') && isDelegation) {
      recentlyCompletedMemberIds = [
        payload.memberId,
        ...recentlyCompletedMemberIds.filter((id) => id !== payload.memberId),
      ].slice(0, 2);
    }
  }
  const index = base.members.findIndex((m) => m.id === payload.memberId);
  const patch = (member: TeamMember): TeamMember => ({
    ...member,
    name: payload.memberName ?? member.name,
    role: payload.role ?? member.role,
    avatar: payload.avatar ?? member.avatar,
    order: payload.order ?? member.order,
    status,
    summary: payload.summary ?? member.summary,
    hasStarted:
      event === 'started' || event === 'reawakened'
        ? true
        : member.hasStarted,
    firstStartedSeq:
      event === 'started' || event === 'reawakened'
        ? (member.firstStartedSeq ?? seq)
        : member.firstStartedSeq,
    lastCompletedSeq:
      event === 'completed' || event === 'paused'
        ? (seq ?? member.lastCompletedSeq)
        : member.lastCompletedSeq,
    startCount:
      event === 'started'
        ? (member.startCount ?? 0) + 1
        : member.startCount,
    completedCount:
      event === 'completed'
        ? (member.completedCount ?? 0) + 1
        : member.completedCount,
    pausedCount:
      event === 'paused'
        ? (member.pausedCount ?? 0) + 1
        : member.pausedCount,
    reawakenedCount:
      event === 'reawakened'
        ? (member.reawakenedCount ?? 0) + 1
        : member.reawakenedCount,
    errorCount:
      event === 'failed'
        ? (member.errorCount ?? 0) + 1
        : member.errorCount,
    promptTokens:
      payload.promptTokens != null
        ? Math.max(member.promptTokens ?? 0, payload.promptTokens)
        : member.promptTokens,
    completionTokens:
      payload.completionTokens != null
        ? Math.max(member.completionTokens ?? 0, payload.completionTokens)
        : member.completionTokens,
  });
  let members: TeamMember[];
  if (index < 0) {
    members = sortByOrder([
      ...base.members,
      patch({ id: payload.memberId, name: payload.memberId, status }),
    ]);
  } else {
    members = base.members.map((m, i) => (i === index ? patch(m) : m));
  }
  // generator-verifier：把生成者本轮产出摘要写入审计日志当前轮。
  let gv = base.gv;
  if (
    gv &&
    base.pattern === 'generator-verifier' &&
    event === 'completed' &&
    payload.memberId === gv.generatorId &&
    payload.summary
  ) {
    gv = { ...gv, rounds: upsertRound(gv.rounds, gv.iteration, { generateSummary: payload.summary }) };
  }
  return {
    ...base,
    members,
    activeMemberIds: isDelegation ? activeMemberIds : base.activeMemberIds,
    recentlyCompletedMemberIds: isDelegation ? recentlyCompletedMemberIds : base.recentlyCompletedMemberIds,
    phase: 'running',
    anyMemberFailed: base.anyMemberFailed || event === 'failed',
    gv,
  };
}

export function applyLeadSynthesizing(prev: TeamState | null): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    phase: 'synthesizing',
    lead: prev.lead ? { ...prev.lead, status: 'synthesizing' } : prev.lead,
  };
}

function mapSnapshotMemberStatus(
  raw: string | null | undefined,
  delegation: boolean,
): TeamMemberStatus {
  if (raw === 'running' || raw === 'error') {
    return raw;
  }
  if (raw === 'paused') {
    return 'paused';
  }
  if (raw === 'completed') {
    return 'completed';
  }
  return delegation ? 'not-scheduled' : 'idle';
}

/** Merge member statuses from GET /team-snapshot (run_events enrichment). */
export function mergeTeamSnapshotStatuses(
  team: TeamState | null,
  snapshot: TeamSnapshot,
): TeamState | null {
  if (!team) {
    return team;
  }
  const delegation = team.visualizationMode === 'delegation';
  const statusById = new Map(
    snapshot.members.map((member) => [
      member.memberId,
      mapSnapshotMemberStatus(member.status, delegation),
    ]),
  );
  const activeMemberIds = delegation
    ? team.members
        .filter((member) => statusById.get(member.id) === 'running')
        .map((member) => member.id)
    : team.activeMemberIds;
  return {
    ...team,
    activeMemberIds,
    members: team.members.map((member) => ({
      ...member,
      status: statusById.get(member.id) ?? member.status,
    })),
  };
}

export function applyTeamCompleted(prev: TeamState | null): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    phase: 'done',
    lead: prev.lead ? { ...prev.lead, status: 'done' } : prev.lead,
    members: prev.members.map((m) =>
      m.status === 'running' ? { ...m, status: 'completed' as const } : m,
    ),
    gv: prev.gv
      ? {
          ...prev.gv,
          phase: prev.gv.phase === 'accepted' ? 'accepted' : prev.gv.phase,
        }
      : undefined,
  };
}

export function applyTeamCompletedPayload(
  prev: TeamState | null,
  payload: {
    busEntryCount?: number;
    converged?: boolean;
    blackboardVersion?: number;
    convergenceStreak?: number;
    iterationsCompleted?: number;
    busMode?: string;
    topicBusProvider?: string;
  },
): TeamState | null {
  const done = applyTeamCompleted(prev);
  if (!done) {
    return done;
  }
  let next = done;
  if (typeof payload.busEntryCount === 'number') {
    next = { ...next, busEntryCount: payload.busEntryCount };
  }
  if (payload.busMode) {
    next = { ...next, busMode: payload.busMode };
  }
  if (payload.topicBusProvider) {
    next = { ...next, topicBusProvider: payload.topicBusProvider };
  }
  if (next.bus) {
    next = {
      ...next,
      bus: {
        ...next.bus,
        converged: payload.converged ?? next.bus.converged,
        convergenceStreak: payload.convergenceStreak ?? next.bus.convergenceStreak,
        iteration: payload.iterationsCompleted ?? next.bus.iteration,
      },
    };
  }
  if (next.ss) {
    next = {
      ...next,
      ss: {
        ...next.ss,
        converged: payload.converged ?? next.ss.converged,
        blackboardVersion: payload.blackboardVersion ?? next.ss.blackboardVersion,
        convergenceStreak: payload.convergenceStreak ?? next.ss.convergenceStreak,
        iteration: payload.iterationsCompleted ?? next.ss.iteration,
      },
    };
  }
  return next;
}

export function applyStateProgress(
  prev: TeamState | null,
  payload: {
    iteration: number;
    maxIterations: number;
    convergenceStreak: number;
    convergenceTarget: number;
    blackboardVersion?: number;
    roundHadNewFindings?: boolean;
  },
): TeamState | null {
  if (!prev || prev.pattern !== 'shared-state') {
    return prev;
  }
  return {
    ...prev,
    ss: {
      iteration: payload.iteration,
      maxIterations: payload.maxIterations,
      convergenceStreak: payload.convergenceStreak,
      convergenceTarget: payload.convergenceTarget,
      blackboardVersion: payload.blackboardVersion ?? prev.ss?.blackboardVersion,
      converged: prev.ss?.converged,
      roundHadNewFindings: payload.roundHadNewFindings,
    },
  };
}

export function applyIterationStarted(
  prev: TeamState | null,
  payload: TeamIterationPayload,
): TeamState | null {
  if (!prev) {
    return prev;
  }
  if (prev.pattern === 'shared-state') {
    return {
      ...prev,
      phase: 'running',
      members: prev.members.map((m) =>
        m.status === 'running' || m.status === 'completed'
          ? { ...m, status: 'idle' as const }
          : m,
      ),
      ss: {
        iteration: payload.iteration,
        maxIterations: payload.maxIterations,
        convergenceStreak: prev.ss?.convergenceStreak ?? 0,
        convergenceTarget: prev.ss?.convergenceTarget ?? 0,
        blackboardVersion: prev.ss?.blackboardVersion,
        converged: prev.ss?.converged,
      },
    };
  }
  if (prev.pattern === 'message-bus') {
    return {
      ...prev,
      phase: 'running',
      members: prev.members.map((m) =>
        m.status === 'running' ? { ...m, status: 'idle' as const } : m,
      ),
      bus: {
        iteration: payload.iteration,
        maxIterations: payload.maxIterations,
        convergenceStreak: prev.bus?.convergenceStreak ?? 0,
        convergenceTarget: prev.bus?.convergenceTarget ?? 0,
        converged: prev.bus?.converged,
      },
    };
  }
  if (prev.pattern !== 'generator-verifier') {
    return prev;
  }
  return {
    ...prev,
    phase: 'running',
    members:
      payload.iteration > 1
        ? prev.members.map((m) => {
            const gid = prev.gv?.generatorId;
            const vid = prev.gv?.verifierId;
            if (m.id === gid || m.id === vid) {
              return { ...m, status: 'idle' as const };
            }
            return m;
          })
        : prev.members,
    gv: {
      iteration: payload.iteration,
      maxIterations: payload.maxIterations,
      phase: 'generating',
      generatorId: prev.gv?.generatorId,
      verifierId: prev.gv?.verifierId,
      lastFeedback: prev.gv?.lastFeedback,
      rounds: upsertRound(prev.gv?.rounds, payload.iteration, {}),
    },
  };
}

export function applyVerifyStarted(prev: TeamState | null, payload: TeamVerifyPayload): TeamState | null {
  if (!prev || prev.pattern !== 'generator-verifier') {
    return prev;
  }
  const iteration = payload.iteration ?? prev.gv?.iteration ?? 0;
  return {
    ...prev,
    gv: {
      iteration,
      maxIterations: payload.maxIterations ?? prev.gv?.maxIterations ?? 3,
      phase: 'verifying',
      generatorId: prev.gv?.generatorId,
      verifierId: payload.memberId ?? prev.gv?.verifierId,
      lastFeedback: prev.gv?.lastFeedback,
      rounds: upsertRound(prev.gv?.rounds, iteration, {}),
    },
  };
}

export function applyVerifyAccepted(prev: TeamState | null, payload: TeamVerifyPayload): TeamState | null {
  if (!prev || prev.pattern !== 'generator-verifier') {
    return prev;
  }
  const iteration = payload.iteration ?? prev.gv?.iteration ?? 0;
  return {
    ...prev,
    gv: {
      iteration,
      maxIterations: payload.maxIterations ?? prev.gv?.maxIterations ?? 3,
      phase: 'accepted',
      generatorId: prev.gv?.generatorId,
      verifierId: payload.memberId ?? prev.gv?.verifierId,
      lastFeedback: prev.gv?.lastFeedback,
      rounds: upsertRound(prev.gv?.rounds, iteration, { verdict: 'accepted' }),
    },
  };
}

export function applyVerifyRejected(prev: TeamState | null, payload: TeamVerifyPayload): TeamState | null {
  if (!prev || prev.pattern !== 'generator-verifier') {
    return prev;
  }
  const iteration = payload.iteration ?? prev.gv?.iteration ?? 0;
  const feedback = payload.feedback ?? payload.summary;
  return {
    ...prev,
    gv: {
      iteration,
      maxIterations: payload.maxIterations ?? prev.gv?.maxIterations ?? 3,
      phase: 'rejected',
      lastFeedback: feedback,
      generatorId: prev.gv?.generatorId,
      verifierId: payload.memberId ?? prev.gv?.verifierId,
      rounds: upsertRound(prev.gv?.rounds, iteration, {
        verdict: 'rejected',
        feedback,
        programmatic: payload.programmatic ?? false,
      }),
    },
  };
}

/** usage.delta 带 memberId 时，把增量 token 累加到对应成员（计费分账）。 */
export function applyMemberUsage(
  prev: TeamState | null,
  memberId: string,
  deltaPromptTokens: number,
  deltaCompletionTokens: number,
): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    members: prev.members.map((m) =>
      m.id === memberId
        ? {
            ...m,
            promptTokens: (m.promptTokens ?? 0) + deltaPromptTokens,
            completionTokens: (m.completionTokens ?? 0) + deltaCompletionTokens,
          }
        : m,
    ),
  };
}

export function applyMemberToolStart(
  prev: TeamState | null,
  memberId: string,
): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    members: prev.members.map((member) =>
      member.id === memberId
        ? {
            ...member,
            toolCalls: (member.toolCalls ?? 0) + 1,
          }
        : member,
    ),
  };
}

export function applyMemberRuntimeError(
  prev: TeamState | null,
  memberId: string,
): TeamState | null {
  if (!prev) {
    return prev;
  }
  return {
    ...prev,
    members: prev.members.map((member) =>
      member.id === memberId
        ? {
            ...member,
            errorCount: (member.errorCount ?? 0) + 1,
          }
        : member,
    ),
  };
}

export function teamProgress(state: TeamState | null): { done: number; total: number } {
  if (!state) {
    return { done: 0, total: 0 };
  }
  const done = state.members.filter(
    (m) => m.status === 'completed' || m.status === 'error',
  ).length;
  return { done, total: state.members.length };
}

export function teamTotalTokens(state: TeamState | null): number {
  if (!state) {
    return 0;
  }
  return state.members.reduce(
    (sum, m) => sum + (m.promptTokens ?? 0) + (m.completionTokens ?? 0),
    0,
  );
}

/** Delegation bar members: keep all members visible, prioritize active/recent first. */
export function delegationBarMembers(team: TeamState): TeamMember[] {
  const activeIds = new Set(team.activeMemberIds ?? []);
  const recentIds = new Set(team.recentlyCompletedMemberIds ?? []);
  const running = team.members.filter(
    (member) => member.status === 'running' || activeIds.has(member.id),
  );
  const recent = team.members.filter(
    (member) =>
      recentIds.has(member.id) && !running.some((active) => active.id === member.id),
  );
  const others = team.members.filter(
    (member) =>
      !running.some((active) => active.id === member.id)
      && !recent.some((done) => done.id === member.id),
  );
  return [...running, ...recent, ...others];
}

/** Right-rail team section in delegation mode — show full member list. */
export function delegationOverviewMembers(team: TeamState): TeamMember[] {
  return delegationBarMembers(team);
}

function sortByOrder(members: TeamMember[]): TeamMember[] {
  return [...members].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
}

// ── 兼容旧 API（teamStatus.test.ts / TeamStatusRow 仍引用） ─────────────

export function initialTeamMembers(expert: Expert | null | undefined): TeamMember[] {
  return initialTeamState(expert)?.members ?? [];
}

export function applyTeamMemberEvent(
  members: TeamMember[],
  event: 'started' | 'completed' | 'failed',
  memberId: string,
  memberName?: string,
): TeamMember[] {
  const state = applyMemberEvent(
    { pattern: 'orchestrator', collaboration: 'sequential', lead: null, members, phase: 'running', anyMemberFailed: false },
    event,
    { memberId, memberName },
  );
  return state.members;
}

export function finalizeTeamMembers(members: TeamMember[], streaming: boolean): TeamMember[] {
  if (streaming || members.length === 0) {
    return members;
  }
  return members.map((member) =>
    member.status === 'running' ? { ...member, status: 'completed' as const } : member,
  );
}
