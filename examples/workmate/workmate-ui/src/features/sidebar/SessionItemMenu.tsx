import { useEffect, useRef, useState } from 'react';

interface SessionItemMenuProps {
  pinned: boolean;
  archived: boolean;
  onPin: (pinned: boolean) => void;
  onArchive: (archived: boolean) => void;
}

export function SessionItemMenu({
  pinned,
  archived,
  onPin,
  onArchive,
}: SessionItemMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const onPointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onPointerDown);
    return () => document.removeEventListener('mousedown', onPointerDown);
  }, [open]);

  return (
    <div ref={rootRef} className="session-item-menu" onClick={(event) => event.stopPropagation()}>
      <button
        type="button"
        className="session-item-menu-trigger"
        aria-label="任务操作"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        ⋯
      </button>
      {open && (
        <div className="session-item-menu-panel" role="menu">
          {!archived && (
            <button
              type="button"
              role="menuitem"
              onClick={() => {
                onPin(!pinned);
                setOpen(false);
              }}
            >
              {pinned ? '取消置顶' : '置顶'}
            </button>
          )}
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              onArchive(!archived);
              setOpen(false);
            }}
          >
            {archived ? '取消归档' : '归档'}
          </button>
        </div>
      )}
    </div>
  );
}
