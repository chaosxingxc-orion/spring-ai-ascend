import type { MentionType } from '../types/mention';
import { mentionGroupLabel, mentionIcon } from '../lib/mentionParse';

export interface MentionMenuItem {
  type: MentionType;
  id: string;
  path?: string;
  label: string;
  hint?: string;
}

interface MentionMenuProps {
  open: boolean;
  trigger: '@' | '/' | null;
  items: MentionMenuItem[];
  loading?: boolean;
  activeIndex: number;
  onSelect: (item: MentionMenuItem) => void;
  onHover: (index: number) => void;
}

export function MentionMenu({
  open,
  trigger,
  items,
  loading = false,
  activeIndex,
  onSelect,
  onHover,
}: MentionMenuProps) {
  if (!open || !trigger) {
    return null;
  }

  const grouped = items.reduce<Record<MentionType, MentionMenuItem[]>>(
    (acc, item) => {
      acc[item.type] = acc[item.type] ?? [];
      acc[item.type].push(item);
      return acc;
    },
    {} as Record<MentionType, MentionMenuItem[]>,
  );

  let flatIndex = -1;

  return (
    <div className="mention-menu" role="listbox" aria-label={trigger === '/' ? '技能菜单' : '提及菜单'}>
      {loading && <p className="mention-menu-hint">加载中…</p>}
      {!loading && items.length === 0 && (
        <p className="mention-menu-hint">{trigger === '/' ? '没有匹配的技能' : '没有匹配项'}</p>
      )}
      {!loading &&
        (Object.keys(grouped) as MentionType[]).map((type) => (
          <div key={type} className="mention-menu-group">
            <div className="mention-menu-group-label">{mentionGroupLabel(type)}</div>
            {grouped[type].map((item) => {
              flatIndex += 1;
              const index = flatIndex;
              return (
                <button
                  key={`${item.type}-${item.id}`}
                  type="button"
                  role="option"
                  aria-selected={index === activeIndex}
                  className={`mention-menu-item${index === activeIndex ? ' active' : ''}`}
                  onMouseEnter={() => onHover(index)}
                  onClick={() => onSelect(item)}
                >
                  <span className="mention-menu-item-icon" aria-hidden>
                    {mentionIcon(item.type)}
                  </span>
                  <span className="mention-menu-item-text">
                    <span className="mention-menu-item-label">{item.label}</span>
                    {item.hint && <span className="mention-menu-item-hint">{item.hint}</span>}
                  </span>
                </button>
              );
            })}
          </div>
        ))}
    </div>
  );
}
