import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { listArtifacts, readFile } from '../../api/client';
import type { Artifact, Expert, FileContent, PermissionMode } from '../../types/api';
import { initialTeamState } from '../../lib/teamStatus';
import type { TeamState } from '../../lib/teamStatus';
import { TeamCommandCenter } from '../team/TeamCommandCenter';
import { OverviewTeamSection, type TeamMember } from './OverviewTeamSection';
import { resolveTeamUiLabels } from '../../lib/teamUiLabels';
import { WorkspaceTree } from '../WorkspaceTree';
import { AgentMemberViewer } from './AgentMemberViewer';
import { BrowserPreview } from './BrowserPreview';
import { CodeViewer } from './CodeViewer';
import { DetailPanelSash } from './DetailPanelSash';
import { FileVersionPanel } from './FileVersionPanel';
import { ArtifactReportPreview } from './ArtifactReportPreview';
import { ChangesTab } from './ChangesTab';
import { ExpertPreviewContent } from './ExpertPreviewContent';
import { FileTabBar } from './FileTabBar';
import { ImagePreview } from './ImagePreview';
import { PdfPreview } from './PdfPreview';
import { UnsupportedMediaPreview } from './UnsupportedMediaPreview';
import { VideoPreview } from './VideoPreview';
import type { ChatItem } from '../../types/events';
import { memberHistoryLoadingForMember } from '../../lib/memberHistoryLoading';
import {
  isBinaryPreviewPath,
  isHtmlPath,
  isMarkdownPath,
  previewKind,
} from '../../lib/fileLanguage';
import { detectStructuredTable } from '../../lib/structuredPreview';
import { artifactDisplayName } from '../../lib/artifactDisplay';
import { formatFileSize } from '../../lib/formatLocale';

type DetailTab = 'artifacts' | 'files' | 'changes' | 'expert';
type PreviewMode = 'report' | 'source' | 'browser';

interface DetailPanelProps {
  sessionId: string;
  refreshKey: number;
  detailWidth: number;
  onDetailWidthChange: (width: number) => void;
  expert?: Expert | null;
  streaming?: boolean;
  permissionMode?: PermissionMode;
  workspaceKey?: string;
  autoOpenPath?: string | null;
  autoOpenMode?: 'preview' | 'changes' | null;
  onAutoOpenHandled?: () => void;
  focusTab?: DetailTab | null;
  onFocusTabHandled?: () => void;
  focusMember?: { id: string; name: string } | null;
  onFocusMemberHandled?: () => void;
  team?: TeamState | null;
  chatItems?: ChatItem[];
  teamHistoryLoading?: boolean;
}

const BASE_DETAIL_TABS: { id: DetailTab; label: string }[] = [
  { id: 'artifacts', label: '产物' },
  { id: 'files', label: '全部文件' },
  { id: 'changes', label: '变更' },
];

function ArtifactList({
  artifacts,
  loading,
  activePath,
  onSelect,
}: {
  artifacts: Artifact[];
  loading: boolean;
  activePath: string | null;
  onSelect: (path: string) => void;
}) {
  if (loading) {
    return <p className="detail-hint muted">加载中…</p>;
  }
  if (artifacts.length === 0) {
    return <p className="detail-hint muted">暂无产物</p>;
  }
  return (
    <ul className="detail-artifact-list">
      {artifacts.map((artifact) => (
        <li key={artifact.path}>
          <button
            type="button"
            className={`detail-artifact-item${activePath === artifact.path ? ' active' : ''}`}
            onClick={() => onSelect(artifact.path)}
            title={artifact.path}
          >
            <span className="detail-artifact-name">{artifactDisplayName(artifact.path, artifact.name)}</span>
            <span className="detail-artifact-meta">{formatFileSize(artifact.size)}</span>
          </button>
        </li>
      ))}
    </ul>
  );
}

export function DetailPanel({
  sessionId,
  refreshKey,
  detailWidth,
  onDetailWidthChange,
  expert,
  streaming = false,
  workspaceKey: _workspaceKey,
  autoOpenPath,
  autoOpenMode,
  onAutoOpenHandled,
  focusTab,
  onFocusTabHandled,
  focusMember,
  onFocusMemberHandled,
  team,
  chatItems = [],
  teamHistoryLoading = false,
}: DetailPanelProps) {
  const isTeam = expert?.expertType === 'team' || Boolean(team);
  const teamForSidebar = useMemo(
    () => team ?? (isTeam && expert ? initialTeamState(expert) : null),
    [expert, isTeam, team],
  );
  const teamLabels = resolveTeamUiLabels(expert);

  const detailTabs = useMemo(
    () => (expert
      ? [...BASE_DETAIL_TABS, { id: 'expert' as const, label: '专家' }]
      : BASE_DETAIL_TABS),
    [expert],
  );

  const [activeTab, setActiveTab] = useState<DetailTab>('artifacts');
  const [overviewOpen, setOverviewOpen] = useState(true);
  const [showTeamCommandCenter, setShowTeamCommandCenter] = useState(false);
  const [selectedMember, setSelectedMember] = useState<TeamMember | null>(null);
  const memberHistoryLoading = useMemo(() => {
    if (!selectedMember) {
      return false;
    }
    return memberHistoryLoadingForMember(
      selectedMember.id,
      chatItems,
      teamForSidebar,
      teamHistoryLoading,
    );
  }, [selectedMember, chatItems, teamForSidebar, teamHistoryLoading]);
  const previewRootRef = useRef<HTMLDivElement>(null);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);
  const [artifactsLoading, setArtifactsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [openTabs, setOpenTabs] = useState<string[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const [previewCache, setPreviewCache] = useState<Record<string, FileContent>>({});
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewMode, setPreviewMode] = useState<PreviewMode>('report');
  const [showVersions, setShowVersions] = useState(false);
  const [changesFocusPath, setChangesFocusPath] = useState<string | null>(null);

  const isArtifactPath = useCallback(
    (path: string) => artifacts.some((artifact) => artifact.path === path),
    [artifacts],
  );

  const loadArtifacts = useCallback(async () => {
    setArtifactsLoading(true);
    setError(null);
    try {
      const items = await listArtifacts(sessionId);
      setArtifacts(items);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setArtifactsLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadArtifacts();
  }, [loadArtifacts, refreshKey]);

  const openFile = useCallback(
    async (path: string) => {
      setError(null);
      setShowTeamCommandCenter(false);
      setSelectedMember(null);
      const artifactMeta = artifacts.find((artifact) => artifact.path === path);
      const mime = artifactMeta?.mime;
      const binaryPreview = isBinaryPreviewPath(path, mime);
      if (isArtifactPath(path)) {
        setActiveTab('artifacts');
        if (!binaryPreview) {
          setPreviewMode(isHtmlPath(path, mime) ? 'browser' : 'report');
        }
      } else if (isHtmlPath(path, mime)) {
        setPreviewMode('browser');
      }
      setOpenTabs((prev) => (prev.includes(path) ? prev : [...prev, path]));
      setActivePath(path);
      if (binaryPreview) {
        setPreviewCache((prev) => ({
          ...prev,
          [path]: {
            path,
            mime: mime ?? 'application/octet-stream',
            content: '',
            size: artifactMeta?.size ?? 0,
            truncated: false,
          },
        }));
        return;
      }
      setPreviewLoading(true);
      try {
        const content = await readFile(sessionId, path);
        setPreviewCache((prev) => ({ ...prev, [path]: content }));
      } catch (err) {
        setError((err as Error).message);
      } finally {
        setPreviewLoading(false);
      }
    },
    [artifacts, isArtifactPath, sessionId],
  );

  useEffect(() => {
    if (!autoOpenPath) {
      return;
    }
    if (autoOpenMode === 'changes') {
      setActiveTab('changes');
      setChangesFocusPath(autoOpenPath);
    } else {
      void openFile(autoOpenPath);
    }
    onAutoOpenHandled?.();
  }, [autoOpenPath, autoOpenMode, onAutoOpenHandled, openFile]);

  useEffect(() => {
    if (!focusTab) {
      return;
    }
    setActiveTab(focusTab);
    setShowTeamCommandCenter(false);
    setSelectedMember(null);
    onFocusTabHandled?.();
  }, [focusTab, onFocusTabHandled]);

  useEffect(() => {
    if (!focusMember) {
      return;
    }
    const matched = teamForSidebar?.members.find((member) => member.id === focusMember.id);
    setActiveTab('artifacts');
    setShowTeamCommandCenter(false);
    setActivePath(null);
    setSelectedMember({
      id: focusMember.id,
      name: matched?.name ?? focusMember.name,
      status: matched?.status ?? 'idle',
      role: matched?.role,
      profession: matched?.profession,
      order: matched?.order,
      avatar: matched?.avatar,
    });
    onFocusMemberHandled?.();
  }, [focusMember, onFocusMemberHandled, teamForSidebar]);

  const handleCloseTab = (path: string) => {
    setOpenTabs((prev) => {
      const remaining = prev.filter((p) => p !== path);
      setActivePath((current) => (current === path ? remaining.at(-1) ?? null : current));
      return remaining;
    });
    setPreviewCache((prev) => {
      const next = { ...prev };
      delete next[path];
      return next;
    });
  };

  const handleTabChange = (tab: DetailTab) => {
    setActiveTab(tab);
    if (tab === 'expert') {
      setShowTeamCommandCenter(false);
      setSelectedMember(null);
      setActivePath(null);
      return;
    }
    if (tab !== 'artifacts') {
      setShowTeamCommandCenter(false);
      setSelectedMember(null);
    }
  };

  const activePreview = activePath ? previewCache[activePath] : null;
  const canBrowserPreview = Boolean(
    activePreview
    && isHtmlPath(activePreview.path, activePreview.mime)
    && !activePreview.truncated,
  );
  const canReportPreview = Boolean(
    activePreview
    && isArtifactPath(activePreview.path)
    && !previewKind(activePreview.path, activePreview.mime)
    && (
      isMarkdownPath(activePreview.path, activePreview.mime)
      || detectStructuredTable(activePreview.content)
    ),
  );
  const activeMediaKind = activePreview
    ? previewKind(activePreview.path, activePreview.mime)
    : null;

  return (
    <aside className="detail-panel">
      <DetailPanelSash width={detailWidth} onWidthChange={onDetailWidthChange} />
      <nav className="detail-panel-tabs" aria-label="右栏视图">
        {detailTabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`detail-panel-tab${activeTab === tab.id ? ' active' : ''}`}
            onClick={() => handleTabChange(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      <FileTabBar
        tabs={activeTab === 'changes' || activeTab === 'expert' ? [] : openTabs}
        activePath={activePath}
        previewMode={activePreview && previewMode === 'browser' ? 'browser' : null}
        previewRootRef={previewRootRef}
        onSelect={(path) => {
          setShowTeamCommandCenter(false);
          setSelectedMember(null);
          setActivePath(path);
        }}
        onClose={handleCloseTab}
      />

      {activeTab === 'changes' ? (
        <ChangesTab
          sessionId={sessionId}
          refreshKey={refreshKey}
          onOpenFile={(path) => {
            setActiveTab('files');
            void openFile(path);
          }}
          initialSelectedPath={changesFocusPath}
        />
      ) : (
        <div className="detail-panel-body">
          <aside
            className={`detail-panel-sidebar${
              activeTab === 'artifacts' && isTeam ? ' sidebar-artifacts-with-team' : ''
            }`}
          >
            {activeTab === 'artifacts' && (
              <>
                <div className="detail-sidebar-section detail-artifact-section">
                  <button
                    type="button"
                    className="detail-section-toggle"
                    aria-expanded={overviewOpen}
                    onClick={() => setOverviewOpen((open) => !open)}
                  >
                    <span>概览</span>
                    <span className="detail-section-chevron" aria-hidden>
                      {overviewOpen ? '▾' : '▸'}
                    </span>
                  </button>
                  {overviewOpen && (
                    <ArtifactList
                      artifacts={artifacts}
                      loading={artifactsLoading}
                      activePath={activePath}
                      onSelect={(path) => void openFile(path)}
                    />
                  )}
                </div>
                {isTeam && (
                  <OverviewTeamSection
                    team={teamForSidebar}
                    expertTeamRuntime={expert?.teamRuntime}
                    selectedMemberId={selectedMember?.id}
                    onSelectMember={(member) => {
                      setSelectedMember(member);
                      setShowTeamCommandCenter(false);
                      setActivePath(null);
                    }}
                    onOpenCommandCenter={() => {
                      setShowTeamCommandCenter(true);
                      setSelectedMember(null);
                      setActivePath(null);
                    }}
                  />
                )}
              </>
            )}

            {activeTab === 'files' && (
              <div className="detail-sidebar-section">
                <WorkspaceTree
                  sessionId={sessionId}
                  refreshKey={refreshKey}
                  selectedPath={activePath}
                  compact
                  onSelectFile={(path) => void openFile(path)}
                />
              </div>
            )}
          </aside>

          <div className="detail-panel-preview" ref={previewRootRef}>
            {error && <p className="detail-hint error">{error}</p>}
            {selectedMember ? (
              <AgentMemberViewer
                sessionId={sessionId}
                memberId={selectedMember.id}
                memberName={selectedMember.name}
                items={chatItems}
                streaming={streaming}
                historyLoading={memberHistoryLoading}
                onClose={() => setSelectedMember(null)}
              />
            ) : showTeamCommandCenter && isTeam ? (
              <div className="detail-preview-team-wrap">
                <TeamCommandCenter
                  sessionId={sessionId}
                  team={team ?? teamForSidebar}
                  labels={teamLabels}
                />
              </div>
            ) : activeTab === 'expert' && expert ? (
              <ExpertPreviewContent expert={expert} />
            ) : (
              <>
                {previewLoading && !activePreview && (
                  <p className="detail-hint muted">加载预览…</p>
                )}
                {!previewLoading && !activePreview && (
                  <div className="detail-preview-empty">
                    <p>选择产物或文件预览</p>
                  </div>
                )}
                {activePreview && activeMediaKind === 'image' && (
                  <ImagePreview sessionId={sessionId} path={activePreview.path} />
                )}
                {activePreview && activeMediaKind === 'pdf' && (
                  <PdfPreview sessionId={sessionId} path={activePreview.path} />
                )}
                {activePreview && activeMediaKind === 'video' && (
                  <VideoPreview
                    sessionId={sessionId}
                    path={activePreview.path}
                    mime={activePreview.mime}
                  />
                )}
                {activePreview && activeMediaKind === 'office' && (
                  <UnsupportedMediaPreview path={activePreview.path} kind="office" />
                )}
                {activePreview && activeMediaKind === 'unsupported-binary' && (
                  <UnsupportedMediaPreview path={activePreview.path} kind="binary" />
                )}
                {activePreview && !activeMediaKind && canBrowserPreview && previewMode === 'browser' && (
                  <BrowserPreview
                    sessionId={sessionId}
                    path={activePreview.path}
                    onViewSource={() => setPreviewMode('source')}
                  />
                )}
                {activePreview && !activeMediaKind && canReportPreview && previewMode === 'report' && (
                  <ArtifactReportPreview
                    file={activePreview}
                    brandLabel="WorkMate"
                    expertName={expert?.name}
                    showSourceToggle
                    onViewSource={() => setPreviewMode('source')}
                  />
                )}
                {activePreview && !activeMediaKind && (!canReportPreview || previewMode === 'source') && previewMode !== 'browser' && (
                  <div className="detail-preview-source-wrap">
                    <div className="detail-preview-source-bar no-print">
                      {canBrowserPreview && previewMode === 'source' && (
                        <button
                          type="button"
                          className="btn ghost sm"
                          onClick={() => setPreviewMode('browser')}
                        >
                          浏览器预览
                        </button>
                      )}
                      {canReportPreview && previewMode === 'source' && (
                        <button
                          type="button"
                          className="btn ghost sm"
                          onClick={() => setPreviewMode('report')}
                        >
                          返回报告预览
                        </button>
                      )}
                      <button
                        type="button"
                        className={`btn ghost sm${showVersions ? ' active' : ''}`}
                        onClick={() => setShowVersions((v) => !v)}
                      >
                        版本
                      </button>
                    </div>
                    {showVersions && activePath && (
                      <FileVersionPanel
                        sessionId={sessionId}
                        path={activePath}
                        refreshKey={refreshKey}
                        onReverted={() => void openFile(activePath)}
                      />
                    )}
                    <CodeViewer file={activePreview} />
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </aside>
  );
}
