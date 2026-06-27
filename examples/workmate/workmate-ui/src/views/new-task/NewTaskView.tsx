import { useState } from 'react';
import { InputDock } from '../../components/InputDock';
import type { InputDockConfig } from '../../features/input-dock/types';
import { BestPracticesSection } from '../../features/welcome/BestPracticesSection';
import { WelcomeHero } from '../../features/welcome/WelcomeHero';
import { MainHeader } from '../../layouts/MainHeader';
import { FeaturedSection } from '../../views/market/FeaturedSection';
import type { PlaybookCard, WelcomeConfig } from '../../types/welcome';
import { DiscoverSection } from '../../views/discover/DiscoverSection';

interface NewTaskViewProps {
  dock: InputDockConfig;
  welcome: WelcomeConfig;
  draftSeed?: string;
  onInspirationSelect?: (card: PlaybookCard, autoSend: boolean) => void;
  onDiscoverLaunch?: (payload: { initPrompt: string; expertId?: string; title: string }) => void;
  onPlaybookSelect?: (card: PlaybookCard) => void;
}

/** S01 新建任务 — 配置驱动（office/welcome.yaml） */
export function NewTaskView({ dock, welcome, draftSeed, onInspirationSelect, onDiscoverLaunch, onPlaybookSelect }: NewTaskViewProps) {
  const [draft, setDraft] = useState('');

  const effectiveDraft = draftSeed ?? draft;

  const handlePractice = (card: PlaybookCard) => {
    if (onInspirationSelect) {
      onInspirationSelect(card, true);
      return;
    }
    setDraft(card.initPrompt);
  };

  const handleFeatured = (card: PlaybookCard) => {
    if (onInspirationSelect) {
      onInspirationSelect(card, true);
      return;
    }
    setDraft(card.initPrompt);
  };

  return (
    <main className="chat-panel chat-panel-new-task">
      <MainHeader growthPlan={welcome.growthPlan} />
      <div className="new-task-stage">
        <div className="new-task-column">
          <WelcomeHero config={welcome} onChipClick={(text) => setDraft(text)} />
          <InputDock
            {...dock}
            centered
            showMascot
            draftSeed={effectiveDraft}
            onSend={(message, extras) => {
              setDraft('');
              dock.onSend(message, extras);
            }}
          />
          {welcome.homeFeatured.enabled && welcome.marketFeatured.playbooks.length > 0 && (
            <FeaturedSection
              section={welcome.marketFeatured}
              onSelect={handleFeatured}
            />
          )}
          <BestPracticesSection section={welcome.bestPractices} onSelect={handlePractice} />
          {onDiscoverLaunch && (
            <DiscoverSection onLaunch={onDiscoverLaunch} onPlaybookSelect={onPlaybookSelect} />
          )}
        </div>
      </div>
    </main>
  );
}
