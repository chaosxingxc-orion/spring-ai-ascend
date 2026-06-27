import type { Language } from '../features/settings/settingsTypes';

const MESSAGES = {
  zh: {
    'settings.title': '设置',
    'settings.general': '通用',
    'settings.memory': '长期记忆',
    'settings.security': '安全中心',
    'settings.about': '关于',
    'settings.language': '界面语言',
    'settings.language.zh': '简体中文',
    'settings.language.en': 'English',
    'settings.fontSize': '字号',
    'settings.fontSize.sm': '小',
    'settings.fontSize.md': '标准',
    'settings.fontSize.lg': '大',
    'settings.compactMode': '紧凑模式',
    'settings.compactMode.hint': '缩小间距，适合小屏',
    'settings.submitShortcut': '发送快捷键',
    'settings.submitShortcut.enter': 'Enter 发送，Shift+Enter 换行',
    'settings.submitShortcut.cmdEnter': '⌘/Ctrl+Enter 发送，Enter 换行',
    'settings.saved': '设置已保存',
    'settings.security.hint': '查看审计哈希链、筛选与导出记录。',
    'settings.security.openAudit': '打开审计日志',
    'settings.about.version': 'WorkMate Workbench',
    'settings.about.desc': '本地优先的 Agent 工作台 — 会话、专家团、审计与工件协作。',
    'sidebar.settings': '设置',
    'time.justNow': '刚刚',
    'time.secondsAgo': '{n} 秒前',
    'time.minutesAgo': '{n} 分钟前',
    'time.hoursAgo': '{n} 小时前',
    'time.daysAgo': '{n} 天前',
    'chat.checkpointLabel': '新一轮对话',
    'chat.expertSwitchedDefaultFrom': '默认助理',
    'chat.expertSwitchedLabel': '{from} → {to}',
    'chat.expertSwitchedHint': '继续当前对话',
    'chat.aiDisclaimer':
      'AI 生成内容仅供参考，重要决策请人工复核。WorkMate 不会将您的会话数据用于模型训练。',
    'session.maxSessionsTitle': '已达活跃任务上限',
    'session.maxSessionsBody':
      '当前有 {active} 个未归档任务（上限 {max}）。请归档或删除旧任务后再创建新任务。',
    'session.maxSessionsOver': '至少需要归档 {over} 个任务才能新建。',
    'session.maxSessionsStep1': '在左侧任务列表找到不再需要的任务。',
    'session.maxSessionsStep2': '点击任务右侧「⋯」菜单，选择「归档」。',
    'session.maxSessionsStep3': '归档后任务移入「已归档」区，不再占用活跃名额。',
    'session.maxSessionsCandidatesTitle': '建议归档（最久未更新）',
    'session.maxSessionsArchive': '归档',
    'session.maxSessionsArchiving': '归档中…',
    'session.maxSessionsDismiss': '知道了',
    'session.limitBannerAt': '活跃任务 {active}/{max}，已达上限。请至少归档 {over} 个任务。',
    'session.limitBannerNear': '活跃任务 {active}/{max}，接近上限。',
    'session.limitBannerHelp': '如何归档',
    'session.newTaskAtLimit': '已达活跃任务上限，请先归档旧任务',
    'session.autoArchivePolicy': '已开启自动归档：新建任务时将按最久未更新顺序自动归档非置顶任务。',
    'session.autoArchiveBulk': '自动归档 {count} 个最旧任务',
    'session.autoArchiveBulkRunning': '正在自动归档…',
    'session.autoArchivedOne': '已自动归档「{title}」',
    'session.autoArchivedMany': '已自动归档 {count} 个旧任务',
    'session.limitBannerAuto': '活跃任务 {active}/{max}；新建时将自动归档最旧任务',
    'settings.autoArchiveOnCreate': '达上限时自动归档旧任务',
    'settings.autoArchiveOnCreate.hint': '按最久未更新顺序归档，跳过置顶与运行中任务',
    'session.creating': '正在创建任务…',
  },
  en: {
    'settings.title': 'Settings',
    'settings.general': 'General',
    'settings.memory': 'Memory',
    'settings.security': 'Security',
    'settings.about': 'About',
    'settings.language': 'Language',
    'settings.language.zh': '简体中文',
    'settings.language.en': 'English',
    'settings.fontSize': 'Font size',
    'settings.fontSize.sm': 'Small',
    'settings.fontSize.md': 'Default',
    'settings.fontSize.lg': 'Large',
    'settings.compactMode': 'Compact mode',
    'settings.compactMode.hint': 'Tighter spacing for smaller screens',
    'settings.submitShortcut': 'Send shortcut',
    'settings.submitShortcut.enter': 'Enter to send, Shift+Enter for newline',
    'settings.submitShortcut.cmdEnter': '⌘/Ctrl+Enter to send, Enter for newline',
    'settings.saved': 'Settings saved',
    'settings.security.hint': 'Browse audit hash chain, filter, and export records.',
    'settings.security.openAudit': 'Open audit log',
    'settings.about.version': 'WorkMate Workbench',
    'settings.about.desc': 'Local-first agent workbench — sessions, teams, audit, and artifacts.',
    'sidebar.settings': 'Settings',
    'time.justNow': 'Just now',
    'time.secondsAgo': '{n}s ago',
    'time.minutesAgo': '{n}m ago',
    'time.hoursAgo': '{n}h ago',
    'time.daysAgo': '{n}d ago',
    'chat.checkpointLabel': 'New turn',
    'chat.expertSwitchedDefaultFrom': 'Default assistant',
    'chat.expertSwitchedLabel': '{from} → {to}',
    'chat.expertSwitchedHint': 'Continuing this conversation',
    'chat.aiDisclaimer':
      'AI-generated content is for reference only. Verify important decisions manually. WorkMate does not use your session data for model training.',
    'session.maxSessionsTitle': 'Active task limit reached',
    'session.maxSessionsBody':
      'You have {active} active tasks (limit {max}). Archive or remove old tasks before creating a new one.',
    'session.maxSessionsOver': 'Archive at least {over} task(s) to create a new one.',
    'session.maxSessionsStep1': 'Find tasks you no longer need in the sidebar list.',
    'session.maxSessionsStep2': 'Open the ⋯ menu on a task and choose Archive.',
    'session.maxSessionsStep3': 'Archived tasks move to the Archived section and free an active slot.',
    'session.maxSessionsCandidatesTitle': 'Suggested to archive (least recently updated)',
    'session.maxSessionsArchive': 'Archive',
    'session.maxSessionsArchiving': 'Archiving…',
    'session.maxSessionsDismiss': 'Got it',
    'session.limitBannerAt': 'Active tasks {active}/{max} — limit reached. Archive at least {over}.',
    'session.limitBannerNear': 'Active tasks {active}/{max} — approaching the limit.',
    'session.limitBannerHelp': 'How to archive',
    'session.newTaskAtLimit': 'Active task limit reached — archive old tasks first',
    'session.autoArchivePolicy': 'Auto-archive is on: oldest unpinned tasks will be archived when you create a new one.',
    'session.autoArchiveBulk': 'Auto-archive {count} oldest task(s)',
    'session.autoArchiveBulkRunning': 'Auto-archiving…',
    'session.autoArchivedOne': 'Auto-archived “{title}”',
    'session.autoArchivedMany': 'Auto-archived {count} old task(s)',
    'session.limitBannerAuto': 'Active tasks {active}/{max} — oldest will auto-archive on create',
    'settings.autoArchiveOnCreate': 'Auto-archive old tasks at limit',
    'settings.autoArchiveOnCreate.hint': 'LRU order; skips pinned and running tasks',
    'session.creating': 'Creating task…',
  },
} as const;

export type MessageKey = keyof typeof MESSAGES.zh;

let currentLanguage: Language = 'zh';

export function setI18nLanguage(language: Language): void {
  currentLanguage = language;
}

export function getI18nLanguage(): Language {
  return currentLanguage;
}

export function t(key: MessageKey, params?: Record<string, string | number>): string {
  const table = MESSAGES[currentLanguage] ?? MESSAGES.zh;
  let text: string = table[key] ?? MESSAGES.zh[key] ?? key;
  if (params) {
    for (const [name, value] of Object.entries(params)) {
      text = text.replace(`{${name}}`, String(value));
    }
  }
  return text;
}
