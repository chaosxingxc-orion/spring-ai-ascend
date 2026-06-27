/** 截图对齐检查清单 — 文案与结构以 office/welcome.yaml 为准，此处仅保留路由与验收入口 */
export const UI_ALIGNMENT = {
  screens: {
    S01: {
      route: '/new',
      mockup: 'workmate-new-task-hifi.png',
      designRef: 'S01 新建任务',
      configSource: 'office/welcome.yaml',
      welcomeApi: '/api/v1/welcome',
    },
    S06: {
      route: '/market/experts',
      mockup: 'workmate-expert-marketplace-hifi.png',
      configSource: 'office/welcome.yaml#marketFeatured',
      playbooksApi: '/api/v1/playbooks?placement=market-featured',
    },
    S11: {
      route: '/s/:sessionId',
      mockup: 'workmate-three-column-hifi.png',
      designRef: 'S11 三栏对话',
      historyGroups: ['今天', '昨天', '更早'],
      sidebarSections: ['任务', '空间'],
    },
  },
  dockToolbar: {
    mode: 'Craft',
    model: '自动',
    skills: '技能',
    connectApps: '连应用',
    permissions: '默认权限',
  },
  terminology: {
    expert: '专家',
    expertTeam: '专家团',
    skill: '技能',
    connector: '连接器',
    connectApps: '连应用',
    runtimeAgent: 'agent',
    runtimeMultiAgent: 'multi-agent',
    runtimeSkills: 'skills',
    runtimeMcp: 'MCP',
  },
} as const;
