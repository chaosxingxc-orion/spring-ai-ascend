/** S01 mock — 对齐 office/welcome.yaml + workmate-new-task-hifi.png */
export const welcomeS01Fixture = {
  hero: {
    headline: 'WorkMate，我帮你',
    title: 'WorkMate',
    tagline: '我帮你',
  },
  dock: {
    placeholderNew: '告诉我你想做什么...',
    placeholderSession: '输入消息，@成员或输入 / 选择工具',
  },
  growthPlan: { label: '来成长计划赚积分', enabled: false },
  bestPractices: {
    title: '不知道做什么，试试最佳实践案例',
    actionLabel: '更多',
    placement: 'home-best-practice',
    enabled: false,
    playbooks: [],
  },
  marketFeatured: {
    title: '精选推荐',
    actionLabel: '查看全部',
    placement: 'market-featured',
    enabled: false,
    playbooks: [],
  },
  homeFeatured: { enabled: false },
  marketSearchPlaceholder: '搜索专家、团队或领域关键词',
  scenes: [
    {
      id: 'coding',
      label: '代码开发',
      icon: '</>',
      defaultScene: false,
      chips: [{ label: '代码审查', icon: '↗' }],
    },
    {
      id: 'working',
      label: '日常办公',
      icon: '💼',
      defaultScene: true,
      chips: [
        { label: '总结要点', icon: '📋' },
        { label: '撰写文案', icon: '✎' },
        { label: '翻译', icon: '🌐' },
      ],
    },
    {
      id: 'design',
      label: '设计创意',
      icon: '📝',
      defaultScene: false,
      chips: [{ label: '界面设计', icon: '◫' }],
    },
  ],
};

export const emptySessionsFixture = [];

export const expertsFixture = [
  {
    id: 'prd-writer',
    name: 'PRD 写手',
    description: '产品文档',
    expertType: 'agent',
    defaultInitPrompt: null,
    category: 'product',
    tags: ['beta'],
    beta: true,
    skillCompatibility: [],
  },
  {
    id: 'product-strategy-team',
    name: '产品策略团队',
    description: '竞品与定价分析',
    expertType: 'team',
    defaultInitPrompt: '分析竞品定价策略，输出结构化报告摘要',
    category: 'product',
    tags: ['产品负责人', '竞品分析师', '用户研究员', '数据分析师'],
    skillCompatibility: [],
    quickPrompts: [
      '分析竞品定价策略，输出结构化报告摘要',
      '对比三家竞品的订阅与定价模式',
    ],
    members: [
      { id: 'prd-writer', name: 'PRD 写手', expertId: 'prd-writer', role: '产品需求撰写专家' },
      { id: 'fund-analyst', name: '基金研究助手', expertId: 'fund-analyst', role: '金融研究分析专家' },
    ],
    coordination: { pattern: 'orchestrator' },
  },
];

export const s11SessionId = 's11-design-session';

export const s11SessionFixture = {
  id: s11SessionId,
  title: '分析竞品定价策略',
  workspaceRoot: '/tmp/workmate-s11',
  workspaceKey: 'default',
  status: 'COMPLETED',
  expertId: 'product-strategy-team',
  permissionMode: 'CRAFT',
  promptTokens: 4200,
  completionTokens: 1800,
  createdAt: '2026-06-18T12:00:00.000Z',
  updatedAt: '2026-06-18T12:00:00.000Z',
};

export const s11SessionsFixture = [s11SessionFixture];

/** S11 对话 + 右栏 — 对齐 workmate-three-column-hifi.png */
export const s11ChatFixture = {
  items: [
    {
      id: 's11-user-1',
      kind: 'user',
      text: '你能分析一下竞品定价吗？',
    },
    {
      id: 's11-assistant-1',
      kind: 'assistant',
      text: '以下是针对不同竞品定价策略的分析总结，涵盖订阅分层、按席位计费与用量计费三类模式。',
    },
  ],
  artifacts: [
    {
      path: '竞品定价策略分析报告.md',
      name: '竞品定价策略分析报告.md',
      mime: 'text/markdown',
      size: 2048,
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
  workspaceEntries: [
    {
      name: 'index.html',
      path: 'index.html',
      type: 'file',
      size: 512,
      mime: 'text/html',
      updatedAt: '2026-06-18T12:00:00.000Z',
    },
  ],
};

export const busSessionId = 'bus-lane-session';

export const busExpertFixture = {
  id: 'content-reactive-bus-team',
  name: '内容反应式总线团队',
  description: '多轮 message-bus 协作',
  expertType: 'team',
  defaultInitPrompt: '撰写一段产品功能简介',
  category: 'product',
  tags: ['内容撰写', '内容审核'],
  skillCompatibility: [],
  members: [
    {
      id: 'content-writer',
      name: '文笔佳',
      expertId: 'content-writer',
      role: '撰写专家',
    },
    {
      id: 'content-reviewer',
      name: '严把关',
      expertId: 'content-reviewer',
      role: '质控专家',
    },
  ],
  coordination: { pattern: 'message-bus' },
  uiLabels: {
    messageBusTitle: '团队协作动态',
    busPublishAction: '补充了最新情报',
  },
};

export const busSessionFixture = {
  id: busSessionId,
  title: '投研快讯补充',
  workspaceRoot: '/tmp/workmate-bus',
  workspaceKey: 'default',
  status: 'COMPLETED',
  expertId: 'content-reactive-bus-team',
  permissionMode: 'CRAFT',
  createdAt: '2026-06-20T10:00:00.000Z',
  updatedAt: '2026-06-20T10:30:00.000Z',
};

export const busSessionsFixture = [busSessionFixture];

export const busChatFixture = {
  items: [
    {
      id: 'bus-user-1',
      kind: 'user',
      text: '根据最新市场快讯补充研报要点',
    },
    {
      id: 'bus-tool-1',
      kind: 'tool',
      toolName: 'workmate_team_bus_publish__bus-lane-session',
      status: 'done',
      args: { topic: 'content-writer', body: '市场快讯要点摘要' },
      result: { success: true, data: { topic: 'content-writer' } },
    },
    {
      id: 'bus-assistant-1',
      kind: 'assistant',
      text: '已根据快讯更新研报要点。',
    },
  ],
  artifacts: [],
  workspaceEntries: [],
};

export const busRunEvents = [
  {
    name: 'team.started',
    data: {
      teamId: 'content-reactive-bus-team',
      pattern: 'message-bus',
      busMode: 'async-subscribe-multiwave',
      topicBusProvider: 'local-in-memory',
      parentRunId: 'parent-bus-1',
      members: [
        { memberId: 'content-writer', memberName: '文笔佳', order: 1 },
        { memberId: 'content-reviewer', memberName: '严把关', order: 2 },
      ],
    },
  },
  {
    name: 'team.bus.published',
    data: {
      topic: 'ingress',
      publishSource: 'orchestrator',
      authorMemberName: '用户',
      preview: '根据最新市场快讯补充研报要点',
    },
  },
  {
    name: 'team.bus.published',
    data: {
      topic: 'content-writer',
      publishSource: 'mid-run',
      authorMemberId: 'content-writer',
      authorMemberName: '文笔佳',
      preview: '市场快讯要点摘要',
    },
  },
  { name: 'team.completed', data: { busEntryCount: 2, converged: true, iterationsCompleted: 1 } },
];
