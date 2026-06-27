interface LeaderTurnCollapseToggleProps {
  collapsed: boolean;
  onToggle: () => void;
}

/** reference-style turn header control — collapses leader reasoning/tools/body for a turn. */
export function LeaderTurnCollapseToggle({ collapsed, onToggle }: LeaderTurnCollapseToggleProps) {
  return (
    <button
      type="button"
      className="leader-turn-collapse-btn btn ghost sm"
      aria-expanded={!collapsed}
      onClick={onToggle}
    >
      {collapsed ? '已完成 ▸' : '已完成 ▾'}
    </button>
  );
}
