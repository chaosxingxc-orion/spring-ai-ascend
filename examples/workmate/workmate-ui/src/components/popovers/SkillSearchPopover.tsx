import { useEffect, useMemo, useRef, useState } from 'react';
import { listSkills } from '../../api/market';
import type { SkillInfo } from '../../types/market';
import { TERM } from '../../lib/terminology';
import {
  loadRecentSkillIds,
  sortSkillsForPopover,
  touchRecentSkillId,
} from '../../lib/skillPopoverSort';
import { ConnectorSwitch } from '../ConnectorSwitch';

interface SkillSearchPopoverProps {
  disabled?: boolean;
  enabledSkillIds?: string[];
  onEnabledSkillIdsChange?: (skillIds: string[]) => void;
  onManageAll?: () => void;
  onSelectSkill?: (skill: SkillInfo) => void;
  catalogSkills?: SkillInfo[];
  onCatalogSkillsChange?: (skills: SkillInfo[]) => void;
}

export function SkillSearchPopover({
  disabled,
  enabledSkillIds = [],
  onEnabledSkillIdsChange,
  onManageAll,
  onSelectSkill,
  catalogSkills,
  onCatalogSkillsChange,
}: SkillSearchPopoverProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [skills, setSkills] = useState<SkillInfo[]>(catalogSkills ?? []);
  const [loading, setLoading] = useState(false);
  const [recentSkillIds, setRecentSkillIds] = useState<string[]>(() => loadRecentSkillIds());
  const rootRef = useRef<HTMLDivElement>(null);

  const normalizedEnabledIds = useMemo(
    () => [...new Set((enabledSkillIds ?? []).map((id) => id.trim()).filter(Boolean))],
    [enabledSkillIds],
  );

  const applySkills = (next: SkillInfo[]) => {
    setSkills(next);
    onCatalogSkillsChange?.(next);
  };

  useEffect(() => {
    if (catalogSkills?.length) {
      setSkills(catalogSkills);
    }
  }, [catalogSkills]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const hasCache = (catalogSkills?.length ?? skills.length) > 0;
    setLoading(!hasCache);
    void listSkills()
      .then(applySkills)
      .finally(() => setLoading(false));
  }, [open]);

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

  const setSessionEnabled = (skillId: string, enabled: boolean) => {
    const current = normalizedEnabledIds;
    const next = enabled
      ? [...new Set([...current, skillId])]
      : current.filter((id) => id !== skillId);
    if (enabled) {
      setRecentSkillIds(touchRecentSkillId(skillId));
    }
    onEnabledSkillIdsChange?.(next);
  };

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const base = q
      ? skills.filter(
          (skill) =>
            skill.name.toLowerCase().includes(q) ||
            skill.id.toLowerCase().includes(q) ||
            skill.description.toLowerCase().includes(q),
        )
      : skills;
    return sortSkillsForPopover(base, normalizedEnabledIds, recentSkillIds);
  }, [skills, query, normalizedEnabledIds, recentSkillIds]);

  const sessionEnabledCount = normalizedEnabledIds.length;

  return (
    <div className="dock-popover-anchor" ref={rootRef}>
      <button
        type="button"
        className={`dock-pill${open ? ' open' : ''}${sessionEnabledCount > 0 ? ' active' : ''}`}
        disabled={disabled}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="dock-pill-icon" aria-hidden>⚒</span>
        {TERM.skill}
        {sessionEnabledCount > 0 ? ` · ${sessionEnabledCount}` : ''} ▾
      </button>
      {open && (
        <div className="dock-popover dock-popover-wide dock-popover-search" role="menu">
          <p className="dock-popover-section-title">
            {TERM.runtimeSkills} · 本会话已启用的技能
          </p>
          <input
            type="search"
            className="dock-popover-search-input"
            placeholder={`搜索${TERM.skill}…`}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
          {loading && skills.length === 0 && <p className="dock-popover-hint">加载中…</p>}
          {!loading || filtered.length > 0
            ? filtered.map((skill) => {
              const sessionEnabled = normalizedEnabledIds.includes(skill.id);
              return (
                <div key={skill.id} className="dock-popover-connector-row">
                  <button
                    type="button"
                    className="dock-popover-connector-main dock-popover-skill-main"
                    onClick={() => {
                      onSelectSkill?.(skill);
                      setOpen(false);
                      setQuery('');
                    }}
                  >
                    <span className="dock-popover-item-label">
                      {skill.name}
                      {skill.installed && <span className="dock-popover-installed">已安装</span>}
                    </span>
                    <span className="dock-popover-item-hint">
                      {TERM.runtimeSkills} · {skill.id}
                      {skill.category ? ` · ${skill.category}` : ''}
                    </span>
                    {!skill.installed && (
                      <span className="dock-popover-item-error">需先在技能市场安装</span>
                    )}
                  </button>
                  <ConnectorSwitch
                    compact
                    checked={sessionEnabled}
                    disabled={!skill.installed}
                    label={sessionEnabled ? '本会话已启用' : '本会话未启用'}
                    onChange={(next) => setSessionEnabled(skill.id, next)}
                  />
                </div>
              );
            })
            : null}
          {!loading && filtered.length === 0 && (
            <p className="dock-popover-hint">没有匹配的技能</p>
          )}
          {onManageAll && (
            <>
              <div className="dock-popover-divider" />
              <button
                type="button"
                role="menuitem"
                className="dock-popover-item dock-popover-link"
                onClick={() => {
                  onManageAll();
                  setOpen(false);
                }}
              >
                管理全部技能
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
