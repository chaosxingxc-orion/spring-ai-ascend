import type { WelcomeConfig } from '../types/welcome';

/** S01 离线兜底 — 对齐 office/welcome.yaml (workmate profile) */
export const WELCOME_FALLBACK: WelcomeConfig = {
  hero: {
    headline: 'WorkMate，我帮你',
    title: 'WorkMate',
    tagline: '我帮你',
  },
  dock: {
    placeholderNew: '告诉我你想做什么...',
    placeholderSession: '今天帮你做些什么？@ 引用对话文件，/ 调用技能与指令',
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
      chips: [
        { label: '代码审查', icon: '↗' },
        { label: '单元测试', icon: '↗' },
      ],
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
  onboarding: {
    enabled: false,
    interests: [],
    sampleTasks: [],
  },
};
