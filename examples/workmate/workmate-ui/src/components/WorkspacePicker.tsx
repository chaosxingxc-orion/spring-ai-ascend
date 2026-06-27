import { useEffect, useRef, useState } from 'react';
import type { WorkspacePreset } from '../types/workspace';
import { GitTaskStarterPanel, type GitSelection } from './GitTaskStarterPanel';

interface WorkspacePickerProps {
  presets: WorkspacePreset[];
  value: string;
  gitBranch?: string;
  gitLabel?: string;
  disabled?: boolean;
  readOnly?: boolean;
  readOnlyLabel?: string;
  onChange?: (workspacePath: string) => void;
  onGitSelectionChange?: (selection: GitSelection | null) => void;
}

export function WorkspacePicker({
  presets,
  value,
  gitBranch,
  gitLabel,
  disabled,
  readOnly,
  readOnlyLabel,
  onChange,
  onGitSelectionChange,
}: WorkspacePickerProps) {
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<'preset' | 'git'>('preset');
  const rootRef = useRef<HTMLDivElement>(null);

  const selected =
    presets.find((preset) => preset.path === value) ??
    presets.find((preset) => preset.path === '') ??
    presets[0];

  const displayLabel = gitLabel ?? readOnlyLabel ?? selected?.name ?? '选择工作空间';

  useEffect(() => {
    if (!open) {
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  if (readOnly) {
    return (
      <div className="workspace-picker workspace-picker-readonly">
        <span className="workspace-picker-icon" aria-hidden>{gitLabel ? '⎇' : '📁'}</span>
        <span className="workspace-picker-label">{displayLabel}</span>
      </div>
    );
  }

  return (
    <div className="workspace-picker-anchor" ref={rootRef}>
      <button
        type="button"
        className={`workspace-picker${open ? ' open' : ''}`}
        disabled={disabled}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="workspace-picker-icon" aria-hidden>{gitLabel ? '⎇' : '📁'}</span>
        <span className="workspace-picker-label">{displayLabel}</span>
        {gitBranch && <span className="workspace-picker-branch">{gitBranch}</span>}
        <span className="workspace-picker-chevron">›</span>
      </button>
      {open && (
        <div className="workspace-picker-menu workspace-picker-menu-wide" role="menu">
          <div className="workspace-picker-tabs" role="tablist">
            <button type="button" role="tab" className={tab === 'preset' ? 'active' : ''} onClick={() => setTab('preset')}>
              预设目录
            </button>
            <button type="button" role="tab" className={tab === 'git' ? 'active' : ''} onClick={() => setTab('git')}>
              Git 仓库
            </button>
          </div>
          {tab === 'preset' ? (
            presets.map((preset) => (
              <button
                key={preset.id}
                type="button"
                role="menuitem"
                className={`workspace-picker-item${preset.path === value && !gitLabel ? ' active' : ''}`}
                onClick={() => {
                  onGitSelectionChange?.(null);
                  onChange?.(preset.path);
                  setOpen(false);
                }}
              >
                <span className="workspace-picker-item-name">{preset.name}</span>
                {preset.description && (
                  <span className="workspace-picker-item-hint">{preset.description}</span>
                )}
              </button>
            ))
          ) : (
            <GitTaskStarterPanel
              onSelect={(selection) => {
                onChange?.(selection.workspacePath);
                onGitSelectionChange?.(selection);
                setOpen(false);
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}
