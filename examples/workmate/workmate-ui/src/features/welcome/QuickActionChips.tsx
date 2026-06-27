import type { WelcomeChip } from '../../types/welcome';
import { chipPrompt } from '../../types/welcome';

interface QuickActionChipsProps {
  chips: WelcomeChip[];
  onChipClick?: (text: string) => void;
}

export function QuickActionChips({ chips, onChipClick }: QuickActionChipsProps) {
  return (
    <div className="quick-chips" role="group" aria-label="快捷操作">
      {chips.map((chip) => (
        <button
          key={chip.label}
          type="button"
          className="quick-chip"
          onClick={() => onChipClick?.(chipPrompt(chip))}
        >
          {chip.icon && (
            <span className="quick-chip-icon" aria-hidden>{chip.icon}</span>
          )}
          {chip.label}
        </button>
      ))}
      <button type="button" className="quick-chip quick-chip-more" disabled title="v0.3">
        更多 ▾
      </button>
    </div>
  );
}
