import { useEffect, useRef, useState } from 'react';

import { IconMoreHorizontal, IconPanelRight, IconSearch } from '../../components/icons/SessionHeaderIcons';
import { STREAM_STAGE_LABELS, type StreamStage } from '../../lib/streamStage';

interface SessionChatHeaderProps {
  title: string;
  titleLoading?: boolean;
  streaming?: boolean;
  streamStage?: StreamStage | null;
  queueDepth?: number;
  onClearQueue?: () => void;
  clearQueueBusy?: boolean;
  searchOpen?: boolean;
  onToggleSearch?: () => void;
  onShare?: () => void;
  shareBusy?: boolean;
  shareToast?: string | null;
  detailPanelVisible?: boolean;
  detailPanelAvailable?: boolean;
  onToggleDetailPanel?: () => void;
  onOpenArtifacts?: () => void;
  archived?: boolean;
  onArchive?: (archived: boolean) => void;
  onImportSidecarNdjson?: () => void;
  onRelaySidecarStream?: () => void;
  sidecarImportBusy?: boolean;
  cloudSessionStatus?: string | null;
  onCloudBadgeClick?: () => void;
  onChangeExpert?: () => void;
}

/** S11 对话顶栏 — workmate-three-column-hifi.png */
export function SessionChatHeader({
  title,
  titleLoading = false,
  streaming,
  streamStage = null,
  queueDepth = 0,
  onClearQueue,
  clearQueueBusy = false,
  searchOpen,
  onToggleSearch,
  onShare,
  shareBusy = false,
  shareToast,
  detailPanelVisible = true,
  detailPanelAvailable = false,
  onToggleDetailPanel,
  onOpenArtifacts,
  archived = false,
  onArchive,
  onImportSidecarNdjson,
  onRelaySidecarStream,
  sidecarImportBusy = false,
  cloudSessionStatus = null,
  onCloudBadgeClick,
  onChangeExpert,
}: SessionChatHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [menuOpen]);

  const hasMenuActions = Boolean(
    onOpenArtifacts || onArchive || onImportSidecarNdjson || onRelaySidecarStream || onChangeExpert,
  );

  return (
    <header className="chat-header session-chat-header">
      <div className="session-chat-header-title">
        {titleLoading ? (
          <span className="session-title-skeleton" aria-label="加载会话标题" />
        ) : (
          <h2>{title}</h2>
        )}
        {cloudSessionStatus && (
          onCloudBadgeClick ? (
            <button
              type="button"
              className={`cloud-session-badge cloud-session-badge-btn status-${cloudSessionStatus.toLowerCase()}`}
              title="查看云 Session 路由详情"
              onClick={onCloudBadgeClick}
            >
              云 · {cloudSessionStatus}
            </button>
          ) : (
            <span className={`cloud-session-badge status-${cloudSessionStatus.toLowerCase()}`} title="关联云 Session">
              云 · {cloudSessionStatus}
            </span>
          )
        )}
      </div>
      <div className="chat-header-actions">
        {(onToggleSearch || (detailPanelAvailable && onToggleDetailPanel)) && (
          <div className="session-header-icon-toolbar" role="group" aria-label="视图工具">
            {onToggleSearch && (
              <button
                type="button"
                className={`session-header-icon-btn${searchOpen ? ' active' : ''}`}
                aria-label="在对话中搜索"
                aria-pressed={searchOpen}
                title="在对话中搜索"
                onClick={onToggleSearch}
              >
                <IconSearch className="session-header-icon" />
              </button>
            )}
            {detailPanelAvailable && onToggleDetailPanel && (
              <button
                type="button"
                className={`session-header-icon-btn${detailPanelVisible ? ' active' : ''}`}
                aria-label={detailPanelVisible ? '隐藏右栏' : '显示右栏'}
                aria-pressed={detailPanelVisible}
                title={detailPanelVisible ? '隐藏产物栏' : '显示产物栏'}
                onClick={onToggleDetailPanel}
              >
                <IconPanelRight className="session-header-icon" />
              </button>
            )}
          </div>
        )}
        {streaming && streamStage && (
          <span className={`stream-stage-chip stage-${streamStage}`} title="当前生成阶段">
            {STREAM_STAGE_LABELS[streamStage]}
          </span>
        )}
        {streaming && !streamStage && <span className="streaming-badge">生成中…</span>}
        {!streaming && queueDepth > 0 && (
          <span className="queue-badge" title="排队中的消息">
            排队 {queueDepth}
            {onClearQueue && (
              <button
                type="button"
                className="queue-clear-btn"
                disabled={clearQueueBusy}
                title="清空排队消息"
                onClick={onClearQueue}
              >
                {clearQueueBusy ? '清空中…' : '清空'}
              </button>
            )}
          </span>
        )}
        <button
          type="button"
          className="session-header-share-btn"
          disabled={!onShare || shareBusy}
          title={onShare ? '分享此任务' : '分享不可用'}
          onClick={onShare}
        >
          {shareBusy ? '分享中…' : '分享'}
        </button>
        {shareToast && (
          <span className="session-share-toast" role="status">{shareToast}</span>
        )}
        <div ref={menuRef} className="session-item-menu session-header-overflow-menu">
          <button
            type="button"
            className="session-header-icon-btn session-item-menu-trigger"
            title="更多操作"
            aria-label="更多操作"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((value) => !value)}
          >
            <IconMoreHorizontal className="session-header-icon" />
          </button>
          {menuOpen && hasMenuActions && (
            <div className="session-item-menu-panel" role="menu">
              {onOpenArtifacts && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    onOpenArtifacts();
                    setMenuOpen(false);
                  }}
                >
                  打开右栏
                </button>
              )}
              {onArchive && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    onArchive(!archived);
                    setMenuOpen(false);
                  }}
                >
                  {archived ? '取消归档' : '归档此任务'}
                </button>
              )}
              {onChangeExpert && (
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    onChangeExpert();
                    setMenuOpen(false);
                  }}
                >
                  更换专家
                </button>
              )}
              {onImportSidecarNdjson && (
                <button
                  type="button"
                  role="menuitem"
                  disabled={sidecarImportBusy}
                  onClick={() => {
                    onImportSidecarNdjson();
                    setMenuOpen(false);
                  }}
                >
                  {sidecarImportBusy ? '导入中…' : '导入 ACP sidecar（NDJSON）'}
                </button>
              )}
              {onRelaySidecarStream && (
                <button
                  type="button"
                  role="menuitem"
                  disabled={sidecarImportBusy}
                  onClick={() => {
                    onRelaySidecarStream();
                    setMenuOpen(false);
                  }}
                >
                  {sidecarImportBusy ? '拉取中…' : '拉取 streamable-http sidecar'}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
