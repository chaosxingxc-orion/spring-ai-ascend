import { useState } from 'react';
import type { SceneMode, WelcomeConfig } from '../../types/welcome';
import { defaultSceneId, heroDisplayText, heroUsesSplitBrand } from '../../types/welcome';
import { QuickActionChips } from './QuickActionChips';
import { ScenePills } from './ScenePills';

interface WelcomeHeroProps {
  config: WelcomeConfig;
  onChipClick?: (text: string) => void;
}

export function WelcomeHero({ config, onChipClick }: WelcomeHeroProps) {
  const initialScene = defaultSceneId(config);
  const [scene, setScene] = useState<SceneMode>(initialScene);

  const activeScene = config.scenes.find((item) => item.id === scene) ?? config.scenes[0];
  const chips = activeScene?.chips ?? [];
  const splitBrand = heroUsesSplitBrand(config);

  return (
    <div className="welcome-hero">
      <div className="welcome-brand">
        {splitBrand ? (
          <>
            <h2 className="welcome-title">{config.hero.title}</h2>
            <p className="welcome-tagline-sub">{config.hero.tagline}</p>
          </>
        ) : (
          <h2 className="welcome-headline">{heroDisplayText(config)}</h2>
        )}
      </div>
      {(config.scenes.length > 0 || chips.length > 0) && (
        <div className="welcome-hero-actions">
          {config.scenes.length > 0 && (
            <ScenePills scenes={config.scenes} value={scene} onChange={setScene} />
          )}
          <QuickActionChips chips={chips} onChipClick={onChipClick} />
        </div>
      )}
    </div>
  );
}
