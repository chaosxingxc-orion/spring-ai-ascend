import type { CSSProperties } from 'react';
import type { PlaybookCard, WelcomeSection } from '../../types/welcome';
import { DiscoverFavoriteButton } from '../../components/DiscoverFavoriteButton';

interface FeaturedSectionProps {
  section: WelcomeSection;
  onSelect: (playbook: PlaybookCard) => void;
}

export function FeaturedSection({ section, onSelect }: FeaturedSectionProps) {
  if (!section.enabled || section.playbooks.length === 0) {
    return null;
  }

  return (
    <section className="featured-section" aria-label="精选推荐">
      <div className="featured-section-header">
        {section.title && <h2 className="market-section-title">{section.title}</h2>}
        {section.actionLabel && (
          <button type="button" className="btn ghost featured-view-all" disabled title="v0.3">
            {section.actionLabel} ›
          </button>
        )}
      </div>
      <div className="featured-scroll">
        {section.playbooks.map((playbook) => (
          <div key={playbook.id} className="playbook-card-wrap">
            <button
              type="button"
              className="playbook-card"
              style={
                playbook.accent
                  ? ({ '--playbook-accent': playbook.accent } as CSSProperties)
                  : undefined
              }
              onClick={() => onSelect(playbook)}
            >
              <span className="playbook-card-title">{playbook.title}</span>
              {playbook.description && (
                <span className="playbook-card-desc">{playbook.description}</span>
              )}
            </button>
            <DiscoverFavoriteButton type="playbook" id={playbook.id} compact />
          </div>
        ))}
      </div>
    </section>
  );
}

export type { PlaybookCard };
