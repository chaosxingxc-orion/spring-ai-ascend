import { useEffect } from 'react';
import {
  DEV_AGENTS_PATH,
  DEV_SKILLS_PATH,
  DEV_STUDIO_PATH,
  DEV_TEAMS_PATH,
  DEV_PLAYBOOKS_PATH,
  DEV_RUNTIME_PATH,
  devAgentEditorPath,
  devPlaybookEditorPath,
  devSkillEditorPath,
  devTeamEditorPath,
  parseDevAgentId,
  parseDevPlaybookId,
  parseDevSkillId,
  parseDevStudioSection,
  parseDevTeamId,
  sessionPath,
} from '../../lib/paths';
import { AgentEditorPanel } from './AgentEditorPanel';
import { AgentListPanel } from './AgentListPanel';
import { DevStudioNav } from './DevStudioNav';
import { SkillEditorPanel } from './SkillEditorPanel';
import { SkillListPanel } from './SkillListPanel';
import { TeamEditorPanel } from './TeamEditorPanel';
import { TeamListPanel } from './TeamListPanel';
import { PlaybookEditorPanel } from './PlaybookEditorPanel';
import { PlaybookListPanel } from './PlaybookListPanel';
import { WelcomeEditorPanel } from './WelcomeEditorPanel';
import { DevStudioReloadButton } from './DevStudioReloadButton';
import { DevStudioExportButton } from './DevStudioExportButton';
import { RuntimeConfigPanel } from './RuntimeConfigPanel';
import { useStudioConfig } from '../../hooks/useStudioConfig';

export interface DevStudioNavigateOptions {
  state?: {
    initialMessage?: string;
  };
}

interface DevStudioViewProps {
  pathname: string;
  onNavigate: (path: string, options?: DevStudioNavigateOptions) => void;
}

export function DevStudioView({ pathname, onNavigate }: DevStudioViewProps) {
  const { config, loading: configLoading } = useStudioConfig();
  const section = parseDevStudioSection(pathname);
  const agentId = parseDevAgentId(pathname);
  const skillId = parseDevSkillId(pathname);
  const teamId = parseDevTeamId(pathname);
  const playbookId = parseDevPlaybookId(pathname);

  useEffect(() => {
    if (pathname === DEV_STUDIO_PATH) {
      onNavigate(DEV_AGENTS_PATH);
    }
  }, [pathname, onNavigate]);

  const showAgentEditor = Boolean(agentId);
  const showSkillEditor = Boolean(skillId);
  const showTeamEditor = Boolean(teamId);
  const showPlaybookEditor = Boolean(playbookId);

  if (!configLoading && !config.enabled) {
    return (
      <main className="dev-studio-page">
        <p className="dev-studio-error">开发者控制台已通过特性开关关闭（workmate.studio.enabled=false）。</p>
      </main>
    );
  }

  return (
    <main className="dev-studio-page">
      <header className="dev-studio-header">
        <div>
          <h1>开发者控制台</h1>
          <p className="muted">编辑专家、专家团、技能、Playbook 与 welcome 草稿，保存后即时生效（无需重启服务）</p>
        </div>
        <div className="dev-studio-header-tools">
          <DevStudioReloadButton />
          <DevStudioExportButton />
        </div>
      </header>

      <DevStudioNav />

      <div className="dev-studio-content">
      {showTeamEditor && teamId ? (
        <TeamEditorPanel
          teamId={teamId}
          onBack={() => onNavigate(DEV_TEAMS_PATH)}
          onOpenSession={(sessionId, initialMessage) =>
            onNavigate(sessionPath(sessionId), initialMessage ? { state: { initialMessage } } : undefined)
          }
          onCreated={(id) => onNavigate(devTeamEditorPath(id))}
          onOpenSkill={(id) => onNavigate(devSkillEditorPath(id))}
          onOpenRuntime={() => onNavigate(DEV_RUNTIME_PATH)}
        />
      ) : showPlaybookEditor && playbookId ? (
        <PlaybookEditorPanel
          playbookId={playbookId}
          onBack={() => onNavigate(DEV_PLAYBOOKS_PATH)}
          onCreated={(id) => onNavigate(devPlaybookEditorPath(id))}
        />
      ) : showAgentEditor && agentId ? (
        <AgentEditorPanel
          expertId={agentId}
          onBack={() => onNavigate(DEV_AGENTS_PATH)}
          onOpenSession={(sessionId, initialMessage) =>
            onNavigate(sessionPath(sessionId), initialMessage ? { state: { initialMessage } } : undefined)
          }
          onCreated={(id) => onNavigate(devAgentEditorPath(id))}
          onOpenSkill={(id) => onNavigate(devSkillEditorPath(id))}
          onOpenRuntime={() => onNavigate(DEV_RUNTIME_PATH)}
        />
      ) : showSkillEditor && skillId ? (
        <SkillEditorPanel
          skillId={skillId}
          onBack={() => onNavigate(DEV_SKILLS_PATH)}
          onCreated={(id) => onNavigate(devSkillEditorPath(id))}
        />
      ) : section === 'skills' ? (
        <SkillListPanel
          onOpenSkill={(id) => onNavigate(devSkillEditorPath(id))}
          onUploaded={(id) => onNavigate(devSkillEditorPath(id))}
        />
      ) : section === 'welcome' ? (
        <WelcomeEditorPanel />
      ) : section === 'playbooks' ? (
        <PlaybookListPanel onOpenPlaybook={(id) => onNavigate(devPlaybookEditorPath(id))} />
      ) : section === 'runtime' ? (
        <RuntimeConfigPanel />
      ) : section === 'teams' ? (
        <TeamListPanel onOpenTeam={(id) => onNavigate(devTeamEditorPath(id))} />
      ) : (
        <AgentListPanel
          onOpenExpert={(id) => onNavigate(devAgentEditorPath(id))}
          onOpenTeam={(id) => onNavigate(devTeamEditorPath(id))}
          onImported={(id, expertType) =>
            onNavigate(expertType === 'team' ? devTeamEditorPath(id) : devAgentEditorPath(id))
          }
        />
      )}
      </div>
    </main>
  );
}
