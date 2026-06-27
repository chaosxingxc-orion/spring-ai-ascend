import { useEffect, useRef, useState } from 'react';
import type { Expert } from '../types/api';
import { resolveExpertDisplayName } from '../lib/teamUiLabels';
import { TERM, expertKindLabel, expertRuntimeType } from '../lib/terminology';

interface ExpertPickerProps {
  experts: Expert[];
  value: string;
  disabled?: boolean;
  readOnly?: boolean;
  onChange: (expertId: string) => void;
  onViewAll?: () => void;
}

function expertName(experts: Expert[], id: string): string {
  if (!id) {
    return TERM.expert;
  }
  const expert = experts.find((item) => item.id === id);
  return expert ? resolveExpertDisplayName(expert) : id;
}

export function ExpertPicker({
  experts,
  value,
  disabled,
  readOnly,
  onChange,
  onViewAll,
}: ExpertPickerProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

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

  const label = expertName(experts, value);

  if (readOnly) {
    return (
      <span className="dock-pill dock-pill-static" title="会话已绑定专家，不可切换">
        {label}
      </span>
    );
  }

  return (
    <div className="dock-popover-anchor" ref={rootRef}>
      <button
        type="button"
        className={`dock-pill${open ? ' open' : ''}`}
        disabled={disabled}
        aria-expanded={open}
        onClick={() => setOpen((prev) => !prev)}
      >
        <span className="dock-pill-icon" aria-hidden>👤</span>
        {label} ▾
      </button>
      {open && (
        <div className="dock-popover dock-popover-wide" role="menu">
          <button
            type="button"
            role="menuitemradio"
            aria-checked={value === ''}
            className={`dock-popover-item${value === '' ? ' active' : ''}`}
            onClick={() => {
              onChange('');
              setOpen(false);
            }}
          >
            <span className="dock-popover-item-label">默认专家</span>
            <span className="dock-popover-item-hint">自动路由 · agent</span>
          </button>
          {experts.map((expert) => (
            <button
              key={expert.id}
              type="button"
              role="menuitemradio"
              aria-checked={value === expert.id}
              className={`dock-popover-item${value === expert.id ? ' active' : ''}`}
              onClick={() => {
                onChange(expert.id);
                setOpen(false);
              }}
            >
              <span className="dock-popover-item-label">
                {resolveExpertDisplayName(expert)}
                <span className="dock-popover-item-kind">{expertKindLabel(expert.expertType)}</span>
              </span>
              <span className="dock-popover-item-hint">
                {expertRuntimeType(expert.expertType)} · {expert.id}
              </span>
            </button>
          ))}
          {onViewAll && (
            <>
              <div className="dock-popover-divider" />
              <button
                type="button"
                role="menuitem"
                className="dock-popover-item dock-popover-link"
                onClick={() => {
                  onViewAll();
                  setOpen(false);
                }}
              >
                查看全部专家
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
