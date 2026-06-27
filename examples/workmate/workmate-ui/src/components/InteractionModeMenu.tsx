import { useEffect, useMemo, useRef, useState } from 'react';
import type { Expert, PermissionMode } from '../types/api';
import {
  loadRecentAgentExpertIds,
  loadRecentTeamExpertIds,
  sortExpertsForPopover,
  touchRecentAgentExpertId,
  touchRecentTeamExpertId,
} from '../lib/expertPopoverSort';
import { resolveExpertDisplayName } from '../lib/teamUiLabels';
import { TERM, isTeamExpertType, summonActionLabel } from '../lib/terminology';
import type { ExpertMarketKind } from '../lib/expertMarketFilter';
import { permissionModeLabel } from './ModeMenu';

const MODES: { id: PermissionMode; label: string; icon: string; hint: string }[] = [
  { id: 'CRAFT', label: 'Craft', icon: '🪄', hint: '读写 + Bash，默认执行' },
  { id: 'ASK', label: 'Ask', icon: '💬', hint: '只读问答' },
  { id: 'PLAN', label: 'Plan', icon: '📋', hint: '先规划，不写文件' },
];

interface InteractionModeMenuProps {
  value: PermissionMode;
  experts: Expert[];
  selectedExpertId: string;
  disabled?: boolean;
  /** Lock Craft/Ask/Plan switching while keeping summon actions available. */
  modeLocked?: boolean;
  onChange?: (mode: PermissionMode) => void;
  onExpertChange?: (expertId: string) => void;
  onExpertSummon?: (expert: Expert) => void;
  onSummonExpert?: (kind?: ExpertMarketKind) => void;
}

function modeIcon(mode: PermissionMode): string {
  return MODES.find((item) => item.id === mode)?.icon ?? '🪄';
}

export function InteractionModeMenu({
  value,
  experts,
  selectedExpertId,
  disabled,
  modeLocked = false,
  onChange,
  onExpertChange,
  onExpertSummon,
  onSummonExpert,
}: InteractionModeMenuProps) {
  const [open, setOpen] = useState(false);
  const [summonMenu, setSummonMenu] = useState<ExpertMarketKind | null>(null);
  const [recentAgentExpertIds, setRecentAgentExpertIds] = useState<string[]>(() => loadRecentAgentExpertIds());
  const [recentTeamExpertIds, setRecentTeamExpertIds] = useState<string[]>(() => loadRecentTeamExpertIds());
  const rootRef = useRef<HTMLDivElement>(null);

  const agentExperts = useMemo(
    () => sortExpertsForPopover(
      experts.filter((expert) => !isTeamExpertType(expert.expertType)),
      selectedExpertId,
      recentAgentExpertIds,
    ),
    [experts, recentAgentExpertIds, selectedExpertId],
  );
  const teamExperts = useMemo(
    () => sortExpertsForPopover(
      experts.filter((expert) => isTeamExpertType(expert.expertType)),
      selectedExpertId,
      recentTeamExpertIds,
    ),
    [experts, recentTeamExpertIds, selectedExpertId],
  );

  useEffect(() => {
    if (!open) {
      setSummonMenu(null);
      return;
    }
    const onDocClick = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
        setSummonMenu(null);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const label = permissionModeLabel(value);
  const summonEnabled = Boolean(onSummonExpert || onExpertSummon || onExpertChange);
  const selectedExpert = useMemo(
    () => experts.find((expert) => expert.id === selectedExpertId) ?? null,
    [experts, selectedExpertId],
  );
  const pillLabel = selectedExpert ? resolveExpertDisplayName(selectedExpert) : label;

  const handleExpertPick = (expert: Expert) => {
    if (isTeamExpertType(expert.expertType)) {
      setRecentTeamExpertIds(touchRecentTeamExpertId(expert.id));
    } else {
      setRecentAgentExpertIds(touchRecentAgentExpertId(expert.id));
    }
    if (onExpertSummon) {
      onExpertSummon(expert);
    } else {
      onExpertChange?.(expert.id);
    }
    setOpen(false);
    setSummonMenu(null);
  };

  const renderSummonSubmenu = (
    kind: ExpertMarketKind,
    items: Expert[],
    browseLabel: string,
  ) => (
    <div className="interaction-mode-submenu" role="menu">
      {items.map((expert) => (
        <button
          key={expert.id}
          type="button"
          role="menuitemradio"
          aria-checked={selectedExpertId === expert.id}
          className={`dock-popover-item${selectedExpertId === expert.id ? ' active' : ''}`}
          onClick={() => handleExpertPick(expert)}
        >
          <span className="dock-popover-item-label">{resolveExpertDisplayName(expert)}</span>
          <span className="dock-popover-item-hint">
            {summonActionLabel(expert.expertType)} · {expert.id}
          </span>
        </button>
      ))}
      {items.length === 0 && (
        <p className="dock-popover-empty">暂无可用{kind === 'team' ? TERM.expertTeam : TERM.expert}</p>
      )}
      {onSummonExpert && (
        <button
          type="button"
          role="menuitem"
          className="dock-popover-item dock-popover-link"
          onClick={() => {
            onSummonExpert(kind);
            setOpen(false);
            setSummonMenu(null);
          }}
        >
          {browseLabel}
        </button>
      )}
    </div>
  );

  if (modeLocked && !summonEnabled) {
    return (
      <span className="dock-pill dock-pill-static" title="会话模式（创建时固定）">
        <span className="dock-pill-icon" aria-hidden>{modeIcon(value)}</span>
        {label}
      </span>
    );
  }

  return (
    <div className="dock-popover-anchor" ref={rootRef}>
      <button
        type="button"
        className={`dock-pill${open ? ' open' : ''}${selectedExpert ? ' active' : ''}`}
        disabled={disabled}
        aria-expanded={open}
        title={
          selectedExpert
            ? `${label} · ${resolveExpertDisplayName(selectedExpert)}`
            : modeLocked
              ? `${label} · 可召唤专家`
              : undefined
        }
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="dock-pill-icon" aria-hidden>
          {selectedExpert ? (isTeamExpertType(selectedExpert.expertType) ? '👥' : '💫') : modeIcon(value)}
        </span>
        {pillLabel}{summonEnabled ? ' ▾' : ''}
      </button>
      {open && (
        <div className="dock-popover interaction-mode-popover" role="menu">
          {!modeLocked && MODES.map((mode) => (
            <button
              key={mode.id}
              type="button"
              role="menuitemradio"
              aria-checked={value === mode.id}
              className={`dock-popover-item interaction-mode-item${value === mode.id ? ' active' : ''}`}
              onClick={() => {
                onChange?.(mode.id);
                setOpen(false);
              }}
            >
              <span className="interaction-mode-item-leading" aria-hidden>{mode.icon}</span>
              <span className="dock-popover-item-label">{mode.label}</span>
              {value === mode.id && <span className="interaction-mode-check" aria-hidden>✓</span>}
              <span className="dock-popover-item-hint">{mode.hint}</span>
            </button>
          ))}
          {!modeLocked && summonEnabled && <div className="dock-popover-divider" />}
          {modeLocked && (
            <p className="dock-popover-hint">当前会话模式：{label}（不可切换）</p>
          )}
          {summonEnabled && (
            <>
              <div className="interaction-mode-summon-row">
                <button
                  type="button"
                  role="menuitem"
                  className="dock-popover-item interaction-mode-item interaction-mode-summon"
                  aria-expanded={summonMenu === 'agent'}
                  onClick={() => setSummonMenu((prev) => (prev === 'agent' ? null : 'agent'))}
                >
                  <span className="interaction-mode-item-leading" aria-hidden>💫</span>
                  <span className="dock-popover-item-label">召唤{TERM.expert}</span>
                  <span className="interaction-mode-chevron" aria-hidden>›</span>
                </button>
                {summonMenu === 'agent' && renderSummonSubmenu(
                  'agent',
                  agentExperts,
                  `查看全部${TERM.expert}`,
                )}
              </div>
              <div className="interaction-mode-summon-row">
                <button
                  type="button"
                  role="menuitem"
                  className="dock-popover-item interaction-mode-item interaction-mode-summon"
                  aria-expanded={summonMenu === 'team'}
                  onClick={() => setSummonMenu((prev) => (prev === 'team' ? null : 'team'))}
                >
                  <span className="interaction-mode-item-leading" aria-hidden>👥</span>
                  <span className="dock-popover-item-label">召唤{TERM.expertTeam}</span>
                  <span className="interaction-mode-chevron" aria-hidden>›</span>
                </button>
                {summonMenu === 'team' && renderSummonSubmenu(
                  'team',
                  teamExperts,
                  `查看全部${TERM.expertTeam}`,
                )}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
