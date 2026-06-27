import type { StudioTeamView, StudioTeamWriteBody } from '../types/studio';

export const COORDINATION_PATTERNS = [
  'orchestrator',
  'pipeline',
  'agent-team',
  'generator-verifier',
  'message-bus',
  'shared-state',
] as const;

export const TEAM_RUNTIMES = ['openjiuwen-team', 'workmate-orchestrator'] as const;

const HAS_LEAD = new Set(['orchestrator', 'agent-team']);

export function patternHasLead(pattern: string): boolean {
  return HAS_LEAD.has(pattern);
}

export function patternImpactHint(pattern: string, runtime: string): string {
  const lead = patternHasLead(pattern);
  if (!lead) {
    return `拓扑 ${pattern} 无中心主理人节点；runtime 通常走 workmate-orchestrator。`;
  }
  if (runtime === 'openjiuwen-team') {
    return 'openjiuwen-team：主理人通过 TeamAgent 工具驱动 spawn/mailbox 编排（orchestrator/agent-team）。';
  }
  return 'workmate-orchestrator：Java 编排器按拓扑执行成员 sub-run。';
}

function trimPromptContent(value: string | undefined | null): string {
  return (value ?? '').replace(/\n$/, '');
}

export { trimPromptContent };

export function teamViewToForm(view: StudioTeamView): StudioTeamWriteBody {
  const team = view.team.summary;
  return {
    id: team.id,
    name: team.name,
    description: team.description,
    promptContent: trimPromptContent(view.team.promptContent),
    defaultInitPrompt: team.defaultInitPrompt ?? '',
    category: team.category ?? 'custom',
    tags: team.tags ?? [],
    collaboration: team.collaboration ?? 'sequential',
    teamRuntime: team.teamRuntime ?? 'openjiuwen-team',
    coordination: team.coordination
      ? {
          pattern: team.coordination.pattern ?? 'orchestrator',
          termination: team.coordination.termination ?? undefined,
          acceptanceCriteria: team.coordination.acceptanceCriteria ?? undefined,
        }
      : { pattern: 'orchestrator' },
    lead: team.lead
      ? {
          name: team.lead.name ?? '',
          title: typeof team.lead.title === 'string' ? { zh: team.lead.title } : team.lead.title,
          avatar: team.lead.avatar,
        }
      : { name: team.name + ' 主理人', title: { zh: '团队负责人' } },
    members: view.members.map(({ member, promptContent, expertYaml }) => ({
      id: member.id,
      name: member.name,
      expertId: member.expertId,
      role: member.role,
      order: member.order,
      avatar: member.avatar,
      profession: member.profession,
      backend: 'local',
      promptContent: trimPromptContent(promptContent),
      expertYaml: expertYaml ?? '',
    })),
    maxTurns: team.maxTurns ?? null,
  };
}

export function emptyTeamForm(teamId: string): StudioTeamWriteBody {
  return {
    id: teamId,
    name: '新建专家团',
    description: 'Studio 创建的专家团草稿',
    promptContent: '你是团队主理人，负责协调成员完成任务。',
    category: 'custom',
    tags: ['draft', 'team'],
    collaboration: 'sequential',
    teamRuntime: 'openjiuwen-team',
    coordination: { pattern: 'orchestrator' },
    lead: { name: '主理人', title: { zh: '团队负责人' } },
    members: [
      {
        id: 'member-a',
        name: '成员 A',
        expertId: `${teamId}__member-a`,
        role: '成员 A',
        order: 1,
        backend: 'local',
        promptContent: '你是成员 A。',
      },
      {
        id: 'member-b',
        name: '成员 B',
        expertId: `${teamId}__member-b`,
        role: '成员 B',
        order: 2,
        backend: 'local',
        promptContent: '你是成员 B。',
      },
    ],
    maxTurns: 50,
  };
}
