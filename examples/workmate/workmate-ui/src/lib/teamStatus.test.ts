import { describe, expect, it } from 'vitest';
import {
  applyLeadSynthesizing,
  applyMemberEvent,
  applyTeamCompleted,
  applyTeamCompletedPayload,
  applyTeamBuildCompleted,
  applyTeamMemory,
  applyBusPublished,
  applyStateProgress,
  applyTeamMemberEvent,
  applyIterationStarted,
  applyVerifyAccepted,
  applyVerifyRejected,
  applyVerifyStarted,
  applyTeamStarted,
  initialTeamMembers,
  initialTeamState,
  isTeamBypassMessagingAvailable,
  isTeamMemberFollowUpAvailable,
  teamProgress,
} from './teamStatus';

describe('initialTeamMembers', () => {
  it('seeds members from expert descriptor', () => {
    const members = initialTeamMembers({
      id: 'product-strategy-team',
      name: '产品策略团队',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      members: [
        { id: 'a', name: '成员 A', expertId: 'a' },
        { id: 'b', name: '成员 B', expertId: 'b' },
      ],
    });
    expect(members).toHaveLength(2);
    expect(members[0].status).toBe('idle');
  });
});

describe('applyTeamMemberEvent', () => {
  it('updates member status from SSE events', () => {
    const base = [
      { id: 'a', name: '成员 A', status: 'idle' as const },
      { id: 'b', name: '成员 B', status: 'idle' as const },
    ];
    const running = applyTeamMemberEvent(base, 'started', 'a');
    expect(running[0].status).toBe('running');
    const done = applyTeamMemberEvent(running, 'completed', 'a');
    expect(done[0].status).toBe('completed');
  });
});

describe('TeamState lifecycle', () => {
  const expert = {
    id: 'product-strategy-team',
    name: '产品策略团队',
    description: '',
    expertType: 'team',
    tags: [],
    skillCompatibility: [],
    collaboration: 'sequential',
    lead: { name: '产品策略团长', title: '团队负责人', avatar: '🧭' },
    members: [
      { id: 'prd-writer', name: 'PRD 写手', expertId: 'prd-writer', role: '需求', order: 1, avatar: '📝' },
      { id: 'fund-analyst', name: '基金研究助手', expertId: 'fund-analyst', role: '分析', order: 2, avatar: '📊' },
    ],
  };

  it('seeds lead + ordered members from descriptor', () => {
    const state = initialTeamState(expert)!;
    expect(state.lead?.name).toBe('产品策略团长');
    expect(state.collaboration).toBe('sequential');
    expect(state.pattern).toBe('orchestrator');
    expect(state.members.map((m) => m.id)).toEqual(['prd-writer', 'fund-analyst']);
    expect(state.phase).toBe('idle');
  });

  it('derives pattern from coordination, falling back to collaboration', () => {
    const gv = initialTeamState({ ...expert, coordination: { pattern: 'generator-verifier', termination: { maxIterations: 5 } } })!;
    expect(gv.pattern).toBe('generator-verifier');
    expect(gv.lead).toBeNull();
    expect(gv.gv?.maxIterations).toBe(5);
    expect(gv.gv?.generatorId).toBe('prd-writer');
    const parallel = initialTeamState({ ...expert, coordination: null, collaboration: 'parallel' })!;
    expect(parallel.pattern).toBe('agent-team');
  });

  it('tracks generator-verifier iteration and verify events', () => {
    let state = initialTeamState({
      ...expert,
      coordination: { pattern: 'generator-verifier', termination: { maxIterations: 3 } },
      members: [
        { id: 'gen', name: 'Gen', expertId: 'gen', participantRole: 'generator', order: 1 },
        { id: 'ver', name: 'Ver', expertId: 'ver', participantRole: 'verifier', order: 2 },
      ],
    })!;
    state = applyIterationStarted(state, { iteration: 1, maxIterations: 3 })!;
    expect(state.gv?.phase).toBe('generating');
    state = applyVerifyStarted(state, { memberId: 'ver', iteration: 1, maxIterations: 3 })!;
    expect(state.gv?.phase).toBe('verifying');
    state = applyVerifyRejected(state, { memberId: 'ver', iteration: 1, maxIterations: 3, feedback: 'fix it' })!;
    expect(state.gv?.phase).toBe('rejected');
    expect(state.gv?.lastFeedback).toBe('fix it');
    state = applyVerifyAccepted(state, { memberId: 'ver', iteration: 2, maxIterations: 3 })!;
    expect(state.gv?.phase).toBe('accepted');
  });

  it('accumulates a per-round audit log rebuildable from events', () => {
    let state = initialTeamState({
      ...expert,
      coordination: { pattern: 'generator-verifier', termination: { maxIterations: 3 } },
      members: [
        { id: 'gen', name: 'Gen', expertId: 'gen', participantRole: 'generator', order: 1 },
        { id: 'ver', name: 'Ver', expertId: 'ver', participantRole: 'verifier', order: 2 },
      ],
    })!;
    // 轮 1：生成 → 校验 → 可编程门禁驳回
    state = applyIterationStarted(state, { iteration: 1, maxIterations: 3 })!;
    state = applyMemberEvent(state, 'completed', { memberId: 'gen', summary: '草稿 v1' });
    state = applyVerifyStarted(state, { memberId: 'ver', iteration: 1, maxIterations: 3 })!;
    state = applyVerifyRejected(state, {
      memberId: 'ver',
      iteration: 1,
      maxIterations: 3,
      feedback: '汉字过多',
      programmatic: true,
    })!;
    // 轮 2：生成 → 校验 → 通过
    state = applyIterationStarted(state, { iteration: 2, maxIterations: 3 })!;
    state = applyMemberEvent(state, 'completed', { memberId: 'gen', summary: '草稿 v2' });
    state = applyVerifyStarted(state, { memberId: 'ver', iteration: 2, maxIterations: 3 })!;
    state = applyVerifyAccepted(state, { memberId: 'ver', iteration: 2, maxIterations: 3 })!;

    const rounds = state.gv!.rounds;
    expect(rounds).toHaveLength(2);
    expect(rounds[0]).toMatchObject({
      iteration: 1,
      generateSummary: '草稿 v1',
      verdict: 'rejected',
      feedback: '汉字过多',
      programmatic: true,
    });
    expect(rounds[1]).toMatchObject({
      iteration: 2,
      generateSummary: '草稿 v2',
      verdict: 'accepted',
    });
  });

  it('rebuilds from team.started roster snapshot', () => {
    const state = applyTeamStarted(null, {
      teamId: 'product-strategy-team',
      collaboration: 'sequential',
      lead: { name: '团长', title: '团队负责人' },
      members: [
        { memberId: 'b', memberName: 'B', order: 2 },
        { memberId: 'a', memberName: 'A', order: 1 },
      ],
    });
    expect(state.members.map((m) => m.id)).toEqual(['a', 'b']);
    expect(state.phase).toBe('running');
  });

  it('tracks member progress, synthesis and completion', () => {
    let state = initialTeamState(expert)!;
    state = applyMemberEvent(state, 'started', { memberId: 'prd-writer' });
    expect(teamProgress(state)).toEqual({ done: 0, total: 2 });
    state = applyMemberEvent(state, 'completed', { memberId: 'prd-writer', summary: '产出摘要' });
    expect(teamProgress(state)).toEqual({ done: 1, total: 2 });
    expect(state.members[0].summary).toBe('产出摘要');
    state = applyMemberEvent(state, 'failed', { memberId: 'fund-analyst', error: 'boom' });
    expect(state.anyMemberFailed).toBe(true);
    state = applyLeadSynthesizing(state)!;
    expect(state.phase).toBe('synthesizing');
    expect(state.lead?.status).toBe('synthesizing');
    state = applyTeamCompleted(state)!;
    expect(state.phase).toBe('done');
    expect(state.lead?.status).toBe('done');
  });

  it('enables bypass messaging only while team runtime is live', () => {
    let state = applyTeamBuildCompleted(initialTeamState(expert)!, {
      teamName: 't1',
      displayName: '团队',
    })!;
    expect(isTeamBypassMessagingAvailable(state)).toBe(false);
    state = { ...state, phase: 'running' };
    expect(isTeamBypassMessagingAvailable(state)).toBe(true);
    state = applyLeadSynthesizing(state)!;
    expect(isTeamBypassMessagingAvailable(state)).toBe(true);
    state = applyTeamCompleted(state)!;
    expect(isTeamBypassMessagingAvailable(state)).toBe(false);
  });

  it('enables main-input follow-up only after team completion', () => {
    let state = applyTeamBuildCompleted(initialTeamState(expert)!, {
      teamName: 't1',
      displayName: '团队',
    })!;
    expect(isTeamMemberFollowUpAvailable(state)).toBe(false);
    state = { ...state, phase: 'running' };
    expect(isTeamMemberFollowUpAvailable(state)).toBe(false);
    state = applyTeamCompleted(state)!;
    expect(isTeamMemberFollowUpAvailable(state)).toBe(true);
  });

  it('tracks shared-state progress and memory', () => {
    let state = initialTeamState({
      id: 'research-collab-team',
      name: '协作调研',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      collaboration: 'parallel',
      coordination: {
        pattern: 'shared-state',
        termination: { maxIterations: 4, convergence: 'noNewFindingsForN(2)' },
      },
      members: [
        { id: 'a', name: 'A', expertId: 'a', order: 1 },
        { id: 'b', name: 'B', expertId: 'b', order: 2 },
      ],
    })!;
    expect(state.pattern).toBe('shared-state');
    expect(state.ss?.convergenceTarget).toBe(2);
    state = applyTeamStarted(state, {
      teamId: 'research-collab-team',
      pattern: 'shared-state',
      collaboration: 'parallel',
      maxIterations: 4,
      convergenceTarget: 2,
      members: [
        { memberId: 'a', memberName: 'A', order: 1 },
        { memberId: 'b', memberName: 'B', order: 2 },
      ],
    });
    state = applyIterationStarted(state, { iteration: 1, maxIterations: 4 })!;
    expect(state.ss?.iteration).toBe(1);
    state = applyStateProgress(state, {
      iteration: 1,
      maxIterations: 4,
      convergenceStreak: 0,
      convergenceTarget: 2,
      blackboardVersion: 3,
      roundHadNewFindings: true,
    })!;
    expect(state.ss?.blackboardVersion).toBe(3);
    state = applyTeamMemory(state, {
      path: 'team/run/blackboard.md',
      section: 'A',
      preview: 'insight',
      version: 3,
    });
    expect(state.memoryEntries?.length).toBe(1);
    expect(state.memoryEntries?.[0].version).toBe(3);
    state = applyTeamCompletedPayload(state, { converged: true, blackboardVersion: 5, iterationsCompleted: 3 })!;
    expect(state.ss?.converged).toBe(true);
    expect(state.phase).toBe('done');
  });

  it('tracks message-bus multi-wave iteration', () => {
    let state = initialTeamState({
      id: 'content-reactive-bus-team',
      name: '反应式总线',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      collaboration: 'parallel',
      coordination: {
        pattern: 'message-bus',
        termination: { maxIterations: 2, convergence: 'noNewFindingsForN(2)' },
      },
      members: [
        { id: 'w', name: 'W', expertId: 'w', order: 1 },
        { id: 'r', name: 'R', expertId: 'r', order: 2 },
      ],
    })!;
    state = applyTeamStarted(state, {
      teamId: 'content-reactive-bus-team',
      pattern: 'message-bus',
      collaboration: 'parallel',
      maxIterations: 2,
      convergenceTarget: 2,
      busMode: 'async-subscribe-multiwave',
      members: [
        { memberId: 'w', memberName: 'W', order: 1 },
        { memberId: 'r', memberName: 'R', order: 2 },
      ],
    });
    expect(state.busMode).toBe('async-subscribe-multiwave');
    expect(state.bus?.maxIterations).toBe(2);
    state = applyIterationStarted(state, { iteration: 1, maxIterations: 2 })!;
    expect(state.bus?.iteration).toBe(1);
    state = applyTeamCompletedPayload(state, {
      busMode: 'async-subscribe-multiwave',
      converged: true,
      convergenceStreak: 2,
      iterationsCompleted: 2,
    })!;
    expect(state.bus?.converged).toBe(true);
    expect(state.bus?.iteration).toBe(2);
  });

  it('tracks message-bus lanes from bus events', () => {
    let state = initialTeamState({
      id: 'content-bus-team',
      name: '总线团队',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      collaboration: 'parallel',
      coordination: { pattern: 'message-bus' },
      members: [
        { id: 'w', name: 'W', expertId: 'w', order: 1 },
        { id: 'r', name: 'R', expertId: 'r', order: 2 },
      ],
    })!;
    state = applyBusPublished(state, { topic: 'ingress', authorMemberName: '用户', preview: 'brief', publishSource: 'orchestrator' });
    state = applyBusPublished(state, { topic: 'w', authorMemberId: 'w', preview: 'writer out', publishSource: 'outcome' });
    state = applyBusPublished(state, { topic: 'w', authorMemberId: 'w', preview: 'mid note', publishSource: 'mid-run' });
    expect(state.busLanes?.ingress?.[0].publishSource).toBe('orchestrator');
    expect(state.busLanes?.w?.[0].publishSource).toBe('outcome');
    expect(state.busLanes?.w?.[1].publishSource).toBe('mid-run');
    expect(state.busEntryCount).toBe(3);
  });

  it('delegation mode keeps completed/paused lifecycle', () => {
    let state = applyTeamStarted(null, {
      teamId: 'gpt-researcher-team',
      teamRuntime: 'openjiuwen-team',
      members: [
        { memberId: 'topic-researcher', memberName: '调研员', order: 1 },
        { memberId: 'research-planner', memberName: '规划师', order: 2 },
      ],
    });
    expect(state.visualizationMode).toBe('delegation');
    expect(teamProgress(state)).toEqual({ done: 0, total: 2 });
    expect(state.members[0].status).toBe('not-scheduled');

    state = applyMemberEvent(state, 'started', { memberId: 'topic-researcher', memberName: '调研员' });
    expect(state.activeMemberIds).toEqual(['topic-researcher']);
    expect(state.members[0].status).toBe('running');

    state = applyMemberEvent(state, 'completed', { memberId: 'topic-researcher', memberName: '调研员' });
    expect(state.activeMemberIds).toEqual([]);
    expect(state.members[0].status).toBe('completed');
    expect(teamProgress(state)).toEqual({ done: 1, total: 2 });

    state = applyMemberEvent(state, 'paused', { memberId: 'topic-researcher', memberName: '调研员' });
    expect(state.members[0].status).toBe('paused');
    expect(teamProgress(state)).toEqual({ done: 0, total: 2 });
  });

  it('seeds delegation layout from expert teamRuntime before SSE', () => {
    const state = initialTeamState({
      id: 'gpt-researcher-team',
      name: '深度研究团队',
      description: '',
      expertType: 'team',
      tags: [],
      skillCompatibility: [],
      teamRuntime: 'openjiuwen-team',
      members: [{ id: 'topic-researcher', name: '调研员', expertId: 'x', order: 1 }],
    })!;
    expect(state.visualizationMode).toBe('delegation');
    expect(state.teamRuntime).toBe('openjiuwen-team');
  });

  it('tracks recently completed members in delegation mode', () => {
    let state = applyTeamStarted(null, {
      teamId: 'gpt-researcher-team',
      parentRunId: 'run-1',
      memberCount: 2,
      collaboration: 'sequential',
      pattern: 'orchestrator',
      teamRuntime: 'openjiuwen-team',
      members: [{ memberId: 'topic-researcher', memberName: '谭溯源', order: 1 }],
    })!;
    state = applyMemberEvent(state, 'started', { memberId: 'topic-researcher', memberName: '谭溯源' });
    state = applyMemberEvent(state, 'completed', { memberId: 'topic-researcher', memberName: '谭溯源' });
    expect(state.recentlyCompletedMemberIds).toEqual(['topic-researcher']);
    expect(state.members[0].status).toBe('completed');
  });

  it('preserves teamBuild when team.started arrives again after HITL resume', () => {
    let state = applyTeamStarted(null, {
      teamId: 'chatlaw-team',
      parentRunId: 'run-1',
      teamRuntime: 'openjiuwen-team',
      pattern: 'orchestrator',
      members: [{ memberId: 'info-intake', memberName: '方助理', order: 1 }],
    });
    state = applyTeamBuildCompleted(state, {
      teamName: 'chatlaw-team-run-1',
      displayName: 'chatlaw-divorce-consultation',
    })!;
    state = applyTeamCompleted(state)!;
    expect(state.phase).toBe('done');
    expect(state.teamBuild?.displayName).toBe('chatlaw-divorce-consultation');

    state = applyTeamStarted(state, {
      teamId: 'chatlaw-team',
      parentRunId: 'run-2',
      teamRuntime: 'openjiuwen-team',
      pattern: 'orchestrator',
      members: [{ memberId: 'info-intake', memberName: '方助理', order: 1 }],
    });
    expect(state.phase).toBe('running');
    expect(state.teamBuild?.displayName).toBe('chatlaw-divorce-consultation');
  });
});
