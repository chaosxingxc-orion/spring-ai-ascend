import { useEffect, useRef, useState } from 'react';
import { ModelPopover } from './popovers/ModelPopover';
import type { Expert, ModelCatalog, ModelEffort, PermissionMode } from '../types/api';
import type { ConnectorInfo, SkillInfo } from '../types/market';
import { useEnhancePrompt } from '../hooks/useEnhancePrompt';
import { useSettings } from '../features/settings/SettingsProvider';
import { useMentionMenu } from '../features/input-dock/useMentionMenu';
import { InteractionModeMenu } from './InteractionModeMenu';
import { ModeMenu } from './ModeMenu';
import { ConnectorPopover } from './popovers/ConnectorPopover';
import { SkillSearchPopover } from './popovers/SkillSearchPopover';
import { WorkspacePicker } from './WorkspacePicker';
import type { GitSelection } from './GitTaskStarterPanel';
import { MentionChips } from './MentionChips';
import { MentionMenu } from './MentionMenu';
import type { WorkspacePreset } from '../types/workspace';
import type { MentionRef } from '../types/mention';
import type { SendMessageExtras } from '../types/sendMessage';
import type { UserAttachment } from '../types/events';
import { uploadSessionAttachment } from '../api/attachments';
import { buildArtifactPreviewUrl } from '../lib/previewUrl';
import type { ExpertMarketKind } from '../lib/expertMarketFilter';

interface InputDockProps {
  experts: Expert[];
  selectedExpertId: string;
  permissionMode: PermissionMode;
  sessionLocked: boolean;
  sessionId?: string | null;
  placeholder?: string;
  disabled: boolean;
  streaming: boolean;
  centered?: boolean;
  showMascot?: boolean;
  onExpertChange: (expertId: string) => void;
  onPermissionModeChange: (mode: PermissionMode) => void;
  onOpenMarket?: (tab: 'experts' | 'skills' | 'connectors', kind?: ExpertMarketKind) => void;
  onExpertSummon?: (expert: Expert) => void;
  enabledConnectorIds?: string[];
  onEnabledConnectorIdsChange?: (connectorIds: string[]) => void;
  enabledSkillIds?: string[];
  onEnabledSkillIdsChange?: (skillIds: string[]) => void;
  marketSkills?: SkillInfo[];
  marketConnectors?: ConnectorInfo[];
  onMarketSkillsChange?: (skills: SkillInfo[]) => void;
  onMarketConnectorsChange?: (connectors: ConnectorInfo[]) => void;
  workspacePresets?: WorkspacePreset[];
  selectedWorkspacePath?: string;
  gitBranch?: string;
  gitLabel?: string;
  workspaceReadOnlyLabel?: string;
  onWorkspacePathChange?: (path: string) => void;
  onGitSelectionChange?: (selection: GitSelection | null) => void;
  draftSeed?: string;
  /** One-shot member mention injected from team delegation bar (post-completion follow-up). */
  seedMention?: MentionRef | null;
  onSeedMentionConsumed?: () => void;
  modelCatalog?: ModelCatalog | null;
  modelId?: string;
  effort?: ModelEffort;
  onModelChange?: (modelId: string) => void;
  onEffortChange?: (effort: ModelEffort) => void;
  onSend: (message: string, extras?: SendMessageExtras) => void;
  onStop: () => void;
}

export function InputDock({
  experts,
  selectedExpertId,
  permissionMode,
  sessionLocked,
  sessionId,
  placeholder,
  disabled,
  streaming,
  centered = false,
  showMascot = false,
  onExpertChange,
  onPermissionModeChange,
  onOpenMarket,
  onExpertSummon,
  enabledConnectorIds,
  onEnabledConnectorIdsChange,
  enabledSkillIds,
  onEnabledSkillIdsChange,
  marketSkills,
  marketConnectors,
  onMarketSkillsChange,
  onMarketConnectorsChange,
  workspacePresets = [],
  selectedWorkspacePath = '',
  gitBranch,
  gitLabel,
  workspaceReadOnlyLabel,
  onWorkspacePathChange,
  onGitSelectionChange,
  draftSeed,
  seedMention,
  onSeedMentionConsumed,
  modelCatalog = null,
  modelId = '',
  effort = 'AUTO',
  onModelChange,
  onEffortChange,
  onSend,
  onStop,
}: InputDockProps) {
  type PendingAttachment = {
    id: string;
    attachment?: UserAttachment;
    file?: File;
    previewUrl: string;
  };

  const formRef = useRef<HTMLFormElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [draftEmpty, setDraftEmpty] = useState(true);
  const [pendingAttachments, setPendingAttachments] = useState<PendingAttachment[]>([]);
  const [uploadBusy, setUploadBusy] = useState(false);
  const [attachmentError, setAttachmentError] = useState<string | null>(null);
  const { enhancing, error: enhanceError, enhance, cancel } = useEnhancePrompt(selectedExpertId || undefined);
  const { settings } = useSettings();
  const activeExpert = experts.find((expert) => expert.id === selectedExpertId) ?? null;
  const mentionMenu = useMentionMenu({
    sessionId,
    expert: activeExpert,
    catalogSkills: marketSkills,
    catalogConnectors: marketConnectors,
    onCatalogSkillsChange: onMarketSkillsChange,
    onCatalogConnectorsChange: onMarketConnectorsChange,
  });

  useEffect(() => {
    if (draftSeed && textareaRef.current) {
      textareaRef.current.value = draftSeed;
      textareaRef.current.focus();
      setDraftEmpty(draftSeed.trim().length === 0);
    }
  }, [draftSeed]);

  useEffect(() => {
    if (!seedMention) {
      return;
    }
    mentionMenu.addMention(seedMention);
    setDraftEmpty(false);
    textareaRef.current?.focus();
    onSeedMentionConsumed?.();
  // seedMention is intentionally the only dependency — one-shot inject from delegation bar
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [seedMention]);

  useEffect(() => {
    return () => {
      pendingAttachments.forEach((item) => URL.revokeObjectURL(item.previewUrl));
    };
  }, [pendingAttachments]);

  const syncDraftEmpty = (text: string) => {
    setDraftEmpty(
      text.trim().length === 0 &&
        mentionMenu.pendingMentions.length === 0 &&
        pendingAttachments.length === 0,
    );
  };

  const removePendingAttachment = (id: string) => {
    setPendingAttachments((prev) => {
      const target = prev.find((item) => item.id === id);
      if (target) {
        URL.revokeObjectURL(target.previewUrl);
      }
      return prev.filter((item) => item.id !== id);
    });
  };

  const clearPendingAttachments = () => {
    pendingAttachments.forEach((item) => URL.revokeObjectURL(item.previewUrl));
    setPendingAttachments([]);
  };

  const handleAttachmentPick = async (files: FileList | null) => {
    if (!files?.length || disabled || uploadBusy) {
      return;
    }
    setUploadBusy(true);
    setAttachmentError(null);
    try {
      let skippedCount = 0;
      for (const file of Array.from(files)) {
        if (!file.type.startsWith('image/')) {
          skippedCount += 1;
          continue;
        }
        const previewUrl = URL.createObjectURL(file);
        const id = crypto.randomUUID();
        if (sessionId) {
          try {
            const attachment = await uploadSessionAttachment(sessionId, file);
            setPendingAttachments((prev) => [...prev, { id, attachment, previewUrl }]);
          } catch {
            URL.revokeObjectURL(previewUrl);
          }
        } else {
          setPendingAttachments((prev) => [...prev, { id, file, previewUrl }]);
        }
      }
      if (skippedCount > 0) {
        setAttachmentError(`已忽略 ${skippedCount} 个非图片文件（当前仅支持图片附件）`);
      }
      syncDraftEmpty(textareaRef.current?.value ?? '');
    } finally {
      setUploadBusy(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const handleEnhance = async () => {
    const textarea = textareaRef.current;
    if (!textarea || disabled || streaming) {
      return;
    }
    const enhanced = await enhance(textarea.value);
    if (enhanced) {
      textarea.value = enhanced;
      setDraftEmpty(enhanced.trim().length === 0);
      textarea.focus();
    }
  };

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const input = form.elements.namedItem('message') as HTMLTextAreaElement;
    const text = input.value.trim();
    const mentions = mentionMenu.pendingMentions;
    if ((!text && mentions.length === 0 && pendingAttachments.length === 0) || disabled) {
      return;
    }
    const readyAttachments = pendingAttachments
      .map((item) => item.attachment)
      .filter((item): item is UserAttachment => item != null);
    const localFiles = pendingAttachments
      .map((item) => item.file)
      .filter((item): item is File => item != null);
    onSend(text, {
      mentions: mentions.length > 0 ? mentions : undefined,
      attachments: readyAttachments.length > 0 ? readyAttachments : undefined,
      files: localFiles.length > 0 ? localFiles : undefined,
    });
    input.value = '';
    mentionMenu.clearMentions();
    clearPendingAttachments();
    setDraftEmpty(true);
    mentionMenu.syncFromTextarea('', 0);
  };

  return (
    <div className={`input-dock-wrap${centered ? ' input-dock-centered' : ''}`}>
      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept="image/png, image/jpeg, image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleAttachmentPick(e.target.files)}
      />
      <div className={`input-dock-shell${showMascot ? ' has-mascot' : ''}`}>
        {showMascot && (
          <span className="input-dock-mascot" aria-hidden title="WorkMate">
            🤖
          </span>
        )}
        <form ref={formRef} className="input-dock" onSubmit={handleSubmit}>
        <MentionChips mentions={mentionMenu.pendingMentions} onRemove={mentionMenu.removeMention} />
        {pendingAttachments.length > 0 && (
          <div className="input-dock-attachments" aria-label="待发送附件">
            {pendingAttachments.map((item) => {
              const src =
                item.attachment && sessionId
                  ? buildArtifactPreviewUrl(sessionId, item.attachment.path)
                  : item.previewUrl;
              const label = item.attachment?.name ?? item.file?.name ?? '图片';
              return (
                <span key={item.id} className="input-dock-attachment-chip">
                  <img src={src} alt={label} className="input-dock-attachment-thumb" />
                  <button
                    type="button"
                    className="input-dock-attachment-remove"
                    aria-label={`移除 ${label}`}
                    onClick={() => {
                      removePendingAttachment(item.id);
                      syncDraftEmpty(textareaRef.current?.value ?? '');
                    }}
                  >
                    ×
                  </button>
                </span>
              );
            })}
          </div>
        )}
        <div className="input-dock-compose">
          <textarea
            ref={textareaRef}
            name="message"
            className="input-dock-textarea"
            rows={1}
            placeholder={placeholder ?? '今天帮你做些什么？输入 @ 引用文件/成员，/ 调用技能'}
            disabled={disabled}
            onInput={(event) => {
              const target = event.currentTarget;
              setDraftEmpty(target.value.trim().length === 0 && mentionMenu.pendingMentions.length === 0 && pendingAttachments.length === 0);
              mentionMenu.syncFromTextarea(target.value, target.selectionStart);
            }}
            onKeyDown={(event) => {
              if (mentionMenu.open && mentionMenu.items.length > 0) {
                if (event.key === 'ArrowDown') {
                  event.preventDefault();
                  mentionMenu.moveActive(1);
                  return;
                }
                if (event.key === 'ArrowUp') {
                  event.preventDefault();
                  mentionMenu.moveActive(-1);
                  return;
                }
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  const item = mentionMenu.items[mentionMenu.activeIndex];
                  if (item && textareaRef.current) {
                    mentionMenu.selectItem(item, textareaRef.current);
                    setDraftEmpty(
                      textareaRef.current.value.trim().length === 0 &&
                        mentionMenu.pendingMentions.length === 0 &&
                        pendingAttachments.length === 0,
                    );
                  }
                  return;
                }
                if (event.key === 'Escape') {
                  event.preventDefault();
                  mentionMenu.closeMenu();
                  return;
                }
              }
              if (event.key === 'Enter') {
                const cmdEnter = settings.submitShortcut === 'cmdEnter';
                const shouldSubmit = cmdEnter
                  ? (event.metaKey || event.ctrlKey) && !event.shiftKey
                  : !event.shiftKey;
                if (shouldSubmit) {
                  event.preventDefault();
                  formRef.current?.requestSubmit();
                }
              }
            }}
            onClick={(event) => {
              mentionMenu.syncFromTextarea(event.currentTarget.value, event.currentTarget.selectionStart);
            }}
          />
          <MentionMenu
            open={mentionMenu.open}
            trigger={mentionMenu.trigger}
            items={mentionMenu.items}
            loading={mentionMenu.loading}
            activeIndex={mentionMenu.activeIndex}
            onHover={mentionMenu.setActiveIndex}
            onSelect={(item) => {
              if (textareaRef.current) {
                mentionMenu.selectItem(item, textareaRef.current);
                setDraftEmpty(
                  textareaRef.current.value.trim().length === 0 &&
                    mentionMenu.pendingMentions.length === 0 &&
                    pendingAttachments.length === 0,
                );
              }
            }}
          />
        </div>
        <div className="input-dock-toolbar">
          <div className="input-dock-toolbar-left">
            <InteractionModeMenu
              value={permissionMode}
              experts={experts}
              selectedExpertId={selectedExpertId}
              disabled={disabled}
              onChange={onPermissionModeChange}
              onExpertChange={sessionLocked ? undefined : onExpertChange}
              onExpertSummon={onExpertSummon}
              onSummonExpert={onOpenMarket ? (kind) => onOpenMarket('experts', kind) : undefined}
            />
            <ModelPopover
              catalog={modelCatalog}
              modelId={modelId}
              effort={effort}
              disabled={disabled}
              readOnly={sessionLocked}
              inspectOnly={sessionLocked}
              onModelChange={(id) => onModelChange?.(id)}
              onEffortChange={(value) => onEffortChange?.(value)}
            />
            <SkillSearchPopover
              disabled={disabled}
              enabledSkillIds={enabledSkillIds}
              onEnabledSkillIdsChange={onEnabledSkillIdsChange}
              catalogSkills={marketSkills}
              onCatalogSkillsChange={onMarketSkillsChange}
              onManageAll={onOpenMarket ? () => onOpenMarket('skills') : undefined}
              onSelectSkill={(skill) => {
                mentionMenu.addMention({
                  type: 'skill',
                  id: skill.id,
                  label: skill.name,
                });
                syncDraftEmpty(textareaRef.current?.value ?? '');
                textareaRef.current?.focus();
              }}
            />
            <ConnectorPopover
              disabled={disabled}
              sessionId={sessionId}
              enabledConnectorIds={enabledConnectorIds}
              onEnabledConnectorIdsChange={onEnabledConnectorIdsChange}
              catalogConnectors={marketConnectors}
              onCatalogConnectorsChange={onMarketConnectorsChange}
              onManageAll={onOpenMarket ? () => onOpenMarket('connectors') : undefined}
            />
            <ModeMenu
              value={permissionMode}
              disabled={disabled}
              readOnly={sessionLocked}
            />
          </div>
          <div className="input-dock-actions">
            <button
              type="button"
              className="dock-tool-btn"
              title="添加图片附件"
              disabled={disabled}
              onClick={() => fileInputRef.current?.click()}
            >
              <span className="icon-image" aria-hidden>＋</span>
            </button>
            <button
              type="button"
              className={`dock-tool-btn${enhancing ? ' pulse' : ''}`}
              title="润色提示词"
              disabled={disabled || draftEmpty}
              onClick={enhancing ? cancel : handleEnhance}
            >
              <span className="icon-sparkles" aria-hidden>✨</span>
            </button>
            <button type="button" className="dock-tool-btn" title="语音输入" disabled>
              <span className="icon-mic" aria-hidden>🎙️</span>
            </button>
            {streaming ? (
              <button type="button" className="dock-send stop" onClick={onStop} title="停止">
                ■
              </button>
            ) : (
              <button
                type="submit"
                className="dock-send"
                title="发送"
                disabled={disabled || draftEmpty}
              >
                <span className="dock-send-icon" aria-hidden>➤</span>
              </button>
            )}
          </div>
        </div>
      </form>
      </div>
      {enhanceError && (
        <p className="market-hint error input-dock-enhance-error" role="alert">
          润色失败：{enhanceError}
        </p>
      )}
      {attachmentError && (
        <p className="market-hint error input-dock-enhance-error" role="status">
          {attachmentError}
        </p>
      )}
      {workspacePresets.length > 0 && (
        <WorkspacePicker
          presets={workspacePresets}
          value={selectedWorkspacePath}
          gitBranch={gitBranch}
          gitLabel={gitLabel}
          disabled={disabled}
          readOnly={sessionLocked}
          readOnlyLabel={workspaceReadOnlyLabel}
          onChange={sessionLocked ? undefined : onWorkspacePathChange}
          onGitSelectionChange={sessionLocked ? undefined : onGitSelectionChange}
        />
      )}
      <p className="input-dock-disclaimer">内容由 AI 生成，请核实重要信息</p>
    </div>
  );
}
