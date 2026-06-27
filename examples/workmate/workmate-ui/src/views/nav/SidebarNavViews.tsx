import type { Expert, Session } from '../../types/api';
import type { WorkspacePreset } from '../../types/workspace';
import { organizeActiveSessions } from '../../lib/sessionGroups';
import { groupSessionsByWorkspace } from '../../lib/sessionWorkspace';
import { AutomationJobsPanel } from '../automation/AutomationJobsPanel';
import { AutomationWebhookPanel } from '../automation/AutomationWebhookPanel';
import { CloudSessionsPanel } from '../automation/CloudSessionsPanel';
import { DiscoverSection } from '../discover/DiscoverSection';
import { NavShellPage } from './NavShellPage';
import { NavShellSessionList } from './NavShellSessionList';

interface AssistantHubViewProps {
  sessions: Session[];
  memoryEnabled?: boolean;
  onNewTask: () => void;
  onOpenMarket: () => void;
  onOpenMemorySettings: () => void;
  onSelectSession: (sessionId: string) => void;
}

export function AssistantHubView({
  sessions,
  memoryEnabled = false,
  onNewTask,
  onOpenMarket,
  onOpenMemorySettings,
  onSelectSession,
}: AssistantHubViewProps) {
  const { pinned, groups } = organizeActiveSessions(sessions);
  const recent = [...pinned, ...groups.flatMap((g) => g.sessions)].slice(0, 8);

  return (
    <NavShellPage
      icon="💬"
      title="助理"
      subtitle="Claw"
      description="长期记忆与独立助理线程将在 v1.0 提供。当前可通过下方最近任务或新建任务继续协作。"
      badge={memoryEnabled ? '记忆已开启' : 'v0.3'}
      actions={[
        { label: '新建任务', onClick: onNewTask },
        { label: '浏览专家市场', onClick: onOpenMarket, variant: 'secondary' },
        { label: '记忆设置', onClick: onOpenMemorySettings, variant: 'secondary' },
      ]}
    >
      <section className="nav-shell-section" aria-label="最近任务">
        <h2 className="nav-shell-section-title">最近任务</h2>
        <NavShellSessionList sessions={recent} onSelect={onSelectSession} />
      </section>
    </NavShellPage>
  );
}

interface ProjectsHubViewProps {
  sessions: Session[];
  workspacePresets: WorkspacePreset[];
  experts: Expert[];
  onNewTask: () => void;
  onOpenFiles: () => void;
  onSelectSession: (sessionId: string) => void;
}

export function ProjectsHubView({
  sessions,
  workspacePresets,
  experts: _experts,
  onNewTask,
  onOpenFiles,
  onSelectSession,
}: ProjectsHubViewProps) {
  const workspaceGroups = groupSessionsByWorkspace(
    sessions.filter((s) => !s.archivedAt),
    workspacePresets,
  );
  const teamSessions = sessions
    .filter((s) => !s.archivedAt)
    .slice(0, 12);

  return (
    <NavShellPage
      icon="📁"
      title="项目"
      subtitle="团队协作"
      description="按工作空间整理任务。共享黑板与企业项目空间将在 v1.0 企业版深化。"
      badge="工作空间"
      bullets={[
        '空间分组：侧栏与下方列表按 workspace 聚合',
        '专家团：在市场选择 team 类型专家协作',
        '文件中心：跨任务浏览产物',
      ]}
      actions={[
        { label: '打开文件中心', onClick: onOpenFiles, variant: 'secondary' },
        { label: '新建任务', onClick: onNewTask },
      ]}
    >
      {workspaceGroups.length > 0 ? (
        workspaceGroups.map(({ preset, sessions: groupSessions }) => (
          <section key={preset.path || preset.id} className="nav-shell-section" aria-label={preset.name}>
            <h2 className="nav-shell-section-title">{preset.name}</h2>
            <NavShellSessionList
              sessions={groupSessions}
              emptyLabel="该空间暂无任务"
              onSelect={onSelectSession}
            />
          </section>
        ))
      ) : (
        <section className="nav-shell-section" aria-label="全部任务">
          <h2 className="nav-shell-section-title">全部活跃任务</h2>
          <NavShellSessionList sessions={teamSessions} onSelect={onSelectSession} />
        </section>
      )}
    </NavShellPage>
  );
}

const AUTOMATION_SCRIPTS = [
  { name: 'dogfood-all.sh', desc: 'Smoke：write-hello + PRD + HITL（可加 --with-mcp）' },
  { name: 'dogfood-v03-basics.sh', desc: 'P0 冒烟：健康检查 + 会话 + SSE' },
  { name: 'dogfood-audit-chain.sh', desc: '审计哈希链验证' },
  { name: 'run-dogfood-validators.sh', desc: '离线校验器（无需 LLM）' },
];

export function AutomationHubView({
  experts,
  onOpenSession,
  onOpenMarketConnectors,
}: {
  experts: Expert[];
  onOpenSession: (sessionId: string) => void;
  onOpenMarketConnectors?: () => void;
}) {
  return (
    <NavShellPage
      icon="⏰"
      title="自动化"
      subtitle="定时任务"
      description="创建 Cron 定时任务，自动新建会话并执行 prompt。IM 机器人与流水线集成见下方脚本与 API 说明。"
      badge="W40"
      bullets={[
        'REST API：创建会话、发送 prompt、拉取 run_events',
        'Cursor SDK：程序化驱动 Agent 任务',
        '连接器 OAuth：在市场完成授权后供 Agent 调用',
      ]}
      actions={
        onOpenMarketConnectors
          ? [{ label: '连接器市场', onClick: onOpenMarketConnectors, variant: 'secondary' }]
          : []
      }
    >
      <AutomationJobsPanel experts={experts} onOpenSession={onOpenSession} />
      <CloudSessionsPanel experts={experts} onOpenSession={onOpenSession} />
      <AutomationWebhookPanel />
      <section className="nav-shell-section" aria-label="Smoke 脚本">
        <h2 className="nav-shell-section-title">scripts/dogfood/ 示例</h2>
        <ul className="nav-shell-script-list">
          {AUTOMATION_SCRIPTS.map((script) => (
            <li key={script.name} className="nav-shell-script-item">
              <code>{script.name}</code>
              <span className="muted">{script.desc}</span>
            </li>
          ))}
        </ul>
      </section>
      <section className="nav-shell-section" aria-label="SDK 示例">
        <h2 className="nav-shell-section-title">程序化调用</h2>
        <pre className="nav-shell-code-block">{`POST /api/v1/sessions
{ "title": "定时研报", "expertId": "fund-analyst" }

POST /api/v1/sessions/{id}/messages
{ "text": "生成今日市场摘要" }`}</pre>
      </section>
    </NavShellPage>
  );
}

import type { PlaybookCard } from '../../types/welcome';

export function MoreHubView({
  onDiscoverLaunch,
  onPlaybookSelect,
}: {
  onDiscoverLaunch: (payload: { initPrompt: string; expertId?: string; title: string }) => void;
  onPlaybookSelect?: (playbook: PlaybookCard) => void;
}) {
  return (
    <NavShellPage
      icon="⊞"
      title="更多"
      subtitle="资料库 · 灵感"
      description="收藏的专家、技能与常用启动方式。与新建页「发现」区数据同源。"
      badge="G28 Discover"
    >
      <section className="nav-shell-section" aria-label="发现与收藏">
        <DiscoverSection onLaunch={onDiscoverLaunch} onPlaybookSelect={onPlaybookSelect} />
      </section>
    </NavShellPage>
  );
}
