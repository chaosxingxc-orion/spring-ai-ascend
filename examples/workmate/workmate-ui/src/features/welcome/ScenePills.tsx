import type { SceneMode, WelcomeScene } from '../../types/welcome';

interface ScenePillsProps {
  scenes: WelcomeScene[];
  value: SceneMode;
  onChange: (mode: SceneMode) => void;
}

export function ScenePills({ scenes, value, onChange }: ScenePillsProps) {
  return (
    <div className="scene-pills" role="group" aria-label="场景模式">
      {scenes.map((scene) => (
        <button
          key={scene.id}
          type="button"
          className={`scene-pill${value === scene.id ? ' active' : ''}`}
          aria-pressed={value === scene.id}
          onClick={() => onChange(scene.id)}
        >
          {scene.icon && (
            <span className="scene-pill-icon" aria-hidden>{scene.icon}</span>
          )}
          <span className="scene-pill-label">{scene.label}</span>
        </button>
      ))}
    </div>
  );
}
