import type { Expert, ExpertMember, I18nMap } from '../types/api';

/** expert.yaml `uiLabels` 键 + 拓扑默认文案（Semantic Wrapper）。 */
export interface TeamUiLabels {
  gvTitle: string;
  gvGeneratorBadge: string;
  gvVerifierBadge: string;
  gvGenerating: string;
  gvVerifying: string;
  gvAccepted: string;
  gvRejected: string;
  gvRejectedPrefix: string;
  gvRoundVerdictAccepted: string;
  gvRoundVerdictRejected: string;
  messageBusTitle: string;
  busPublishAction: string;
  busLaneHint: string;
  busLaneLinked: string;
  busLaneIngress: string;
  busLaneBus: string;
  agentTeamTitle: string;
  sharedStateTitle: string;
  approvalTitle: string;
  approvalApprove: string;
  approvalDeny: string;
  approvalAlways: string;
}

const PATTERN_DEFAULTS: Record<string, Partial<TeamUiLabels>> = {
  'generator-verifier': {
    gvTitle: '方案起草与合规审查',
    gvGeneratorBadge: '起草',
    gvVerifierBadge: '合规审查',
    gvGenerating: '起草中',
    gvVerifying: '审查中',
    gvAccepted: '合规审查通过',
    gvRejected: '合规审查未通过',
    gvRejectedPrefix: '合规审查未通过',
    gvRoundVerdictAccepted: '通过',
    gvRoundVerdictRejected: '驳回',
  },
  'message-bus': {
    messageBusTitle: '团队协作动态',
    busPublishAction: '补充了最新情报',
    busLaneIngress: '任务入口',
    busLaneBus: '协作动态',
  },
  'agent-team': {
    agentTeamTitle: '团队并行协作',
  },
  'shared-state': {
    sharedStateTitle: '团队研报区',
  },
};

const GLOBAL_DEFAULTS: TeamUiLabels = {
  gvTitle: '生成校验协作',
  gvGeneratorBadge: '生成',
  gvVerifierBadge: '校验',
  gvGenerating: '生成中',
  gvVerifying: '校验中',
  gvAccepted: '已通过',
  gvRejected: '待修订',
  gvRejectedPrefix: '校验未通过',
  gvRoundVerdictAccepted: '通过',
  gvRoundVerdictRejected: '驳回',
  messageBusTitle: '消息总线',
  busPublishAction: '发布了协作消息',
  busLaneHint: '点击查看泳道对应条目',
  busLaneLinked: '已在泳道高亮',
  busLaneIngress: 'ingress（用户）',
  busLaneBus: '总线',
  agentTeamTitle: '并行 fan-out',
  sharedStateTitle: '共享黑板',
  approvalTitle: '业务确认',
  approvalApprove: '确认提交',
  approvalDeny: '拒绝并修改',
  approvalAlways: '始终允许',
};

function labelOverride(
  overrides: Record<string, string> | undefined,
  key: keyof TeamUiLabels,
): string | undefined {
  if (!overrides) {
    return undefined;
  }
  const value = overrides[key];
  return value?.trim() ? value.trim() : undefined;
}

/** Resolve bilingual map or plain string (default lang: zh). */
export function resolveI18n(
  value: I18nMap | string | undefined | null,
  lang = 'zh',
): string {
  if (!value) {
    return '';
  }
  if (typeof value === 'string') {
    return value.trim();
  }
  if (value[lang]?.trim()) {
    return value[lang].trim();
  }
  if (value.zh?.trim()) {
    return value.zh.trim();
  }
  const first = Object.values(value).find((v) => v?.trim());
  return first?.trim() ?? '';
}

export function resolveExpertDisplayName(expert: Expert | null | undefined, lang = 'zh'): string {
  if (!expert) {
    return '';
  }
  const localized = resolveI18n(expert.displayName, lang);
  return localized || expert.name;
}

export function resolveExpertProfession(expert: Expert | null | undefined, lang = 'zh'): string {
  return resolveI18n(expert?.profession, lang);
}

export function resolveTeamUiLabels(expert?: Expert | null): TeamUiLabels {
  const pattern = expert?.coordination?.pattern ?? '';
  const patternDefaults = PATTERN_DEFAULTS[pattern] ?? {};
  const overrides = expert?.uiLabels;
  const merged = { ...GLOBAL_DEFAULTS, ...patternDefaults };
  const keys = Object.keys(GLOBAL_DEFAULTS) as Array<keyof TeamUiLabels>;
  for (const key of keys) {
    const custom = labelOverride(overrides, key);
    if (custom) {
      merged[key] = custom;
    }
  }
  return merged;
}

function formatMemberPersona(member: ExpertMember, lang = 'zh'): string {
  const profession = resolveI18n(member.profession, lang);
  if (profession) {
    return `${member.name} · ${profession}`;
  }
  return member.name;
}

/** 成员 topic / memberId → 业务展示名（uiLabels.member.<id> 优先）。 */
export function memberDisplayLabel(
  expert: Expert | null | undefined,
  memberOrTopicId: string | undefined,
  lang = 'zh',
): string | undefined {
  if (!memberOrTopicId?.trim()) {
    return undefined;
  }
  const id = memberOrTopicId.trim();
  const custom = expert?.uiLabels?.[`member.${id}`]?.trim();
  if (custom) {
    return custom;
  }
  const member = expert?.members?.find((m) => m.id === id);
  if (!member) {
    return id;
  }
  return formatMemberPersona(member, lang);
}

export function memberDisplayLabelFromMembers(
  members: ExpertMember[] | undefined,
  memberOrTopicId: string | undefined,
  uiLabels?: Record<string, string>,
  lang = 'zh',
): string | undefined {
  if (!memberOrTopicId?.trim()) {
    return undefined;
  }
  const id = memberOrTopicId.trim();
  const custom = uiLabels?.[`member.${id}`]?.trim();
  if (custom) {
    return custom;
  }
  const member = members?.find((m) => m.id === id);
  if (!member) {
    return id;
  }
  return formatMemberPersona(member, lang);
}

export function resolveLeadTitle(
  title: string | I18nMap | undefined | null,
  lang = 'zh',
): string {
  return resolveI18n(title, lang);
}
