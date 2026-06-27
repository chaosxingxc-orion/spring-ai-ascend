import type { CSSProperties } from 'react';
import type { PlaybookCard, WelcomeSection } from '../../types/welcome';

interface BestPracticesSectionProps {
  section: WelcomeSection;
  onSelect?: (card: PlaybookCard) => void;
}

/** 首页底部「最佳实践案例」— 数据来自 welcome.bestPractices */
export function BestPracticesSection({ section, onSelect }: BestPracticesSectionProps) {
  if (!section.enabled || section.playbooks.length === 0) {
    return null;
  }

  return (
    <section className="best-practices-section" aria-label="最佳实践案例">
      <header className="best-practices-header">
        {section.title && <h3 className="best-practices-title">{section.title}</h3>}
        {section.actionLabel && (
          <button type="button" className="btn ghost best-practices-more" disabled title="v0.3">
            {section.actionLabel} ›
          </button>
        )}
      </header>
      <div className="best-practices-grid">
        {section.playbooks.map((card) => (
          <button
            key={card.id}
            type="button"
            className="best-practice-card"
            style={
              card.accent
                ? ({ '--practice-accent': card.accent } as CSSProperties)
                : undefined
            }
            onClick={() => onSelect?.(card)}
          >
            <div className="best-practice-preview" aria-hidden />
            <span className="best-practice-title">{card.title}</span>
          </button>
        ))}
      </div>
    </section>
  );
}
