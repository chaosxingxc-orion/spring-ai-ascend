import { useCallback, useEffect, useState } from 'react';
import { getStudioExpertCapabilities } from '../../api/studio';
import { sourceLabel } from '../../lib/studioForm';
import { TERM } from '../../lib/terminology';
import type { OfficeAssetSource, StudioExpertCapabilityItem } from '../../types/studio';

interface StudioExpertCapabilitiesPanelProps {
  expertId: string;
  onOpenSkill?: (skillId: string) => void;
  onOpenRuntime?: () => void;
}

function CapabilityList({
  title,
  hint,
  items,
  kind,
  onOpenSkill,
  onOpenRuntime,
}: {
  title: string;
  hint: string;
  items: StudioExpertCapabilityItem[];
  kind: 'skill' | 'connector';
  onOpenSkill?: (skillId: string) => void;
  onOpenRuntime?: () => void;
}) {
  if (items.length === 0) {
    return null;
  }

  return (
    <section className="dev-studio-capability-section">
      <header className="dev-studio-capability-header">
        <h3>{title}</h3>
        <p className="muted">{hint}</p>
      </header>
      <ul className="dev-studio-capability-list">
        {items.map((item) => (
          <li key={item.id} className={`dev-studio-capability-item${item.found ? '' : ' missing'}`}>
            <div className="dev-studio-capability-main">
              <code className="dev-studio-capability-id">{item.id}</code>
              {item.found && item.name !== item.id && <span className="dev-studio-capability-name">{item.name}</span>}
              {!item.found && <span className="dev-studio-capability-missing">未在 registry 中找到</span>}
            </div>
            {item.description && <p className="muted dev-studio-capability-desc">{item.description}</p>}
            <div className="dev-studio-capability-meta">
              {item.found && item.source && (
                <span className={`dev-studio-badge dev-studio-badge-${item.source.toLowerCase()}`}>
                  {sourceLabel(item.source as OfficeAssetSource)}
                </span>
              )}
              {kind === 'skill' && item.found && onOpenSkill && (
                <button type="button" className="btn ghost sm" onClick={() => onOpenSkill(item.id)}>
                  打开技能编辑器
                </button>
              )}
              {kind === 'connector' && item.found && onOpenRuntime && (
                <button type="button" className="btn ghost sm" onClick={onOpenRuntime}>
                  查看运行时 MCP
                </button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function StudioExpertCapabilitiesPanel({
  expertId,
  onOpenSkill,
  onOpenRuntime,
}: StudioExpertCapabilitiesPanelProps) {
  const [capabilities, setCapabilities] = useState<Awaited<ReturnType<typeof getStudioExpertCapabilities>> | null>(
    null,
  );
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCapabilities(await getStudioExpertCapabilities(expertId));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, [expertId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <p className="muted">加载关联能力…</p>;
  }

  if (error) {
    return <div className="dev-studio-error">{error}</div>;
  }

  if (!capabilities) {
    return null;
  }

  const total =
    capabilities.skills.length + capabilities.connectors.length + capabilities.unresolved.length;
  if (total === 0) {
    return (
      <section className="dev-studio-capabilities">
        <header className="dev-studio-skill-dir-header">
          <h2>关联能力</h2>
          <p className="muted">可在左侧表单填写 skillCompatibility（连接器/MCP）与 preloadSkills（预载技能）</p>
        </header>
        <p className="muted dev-studio-empty">尚未配置推荐技能或 MCP 连接器</p>
      </section>
    );
  }

  return (
    <section className="dev-studio-capabilities">
      <header className="dev-studio-skill-dir-header">
        <h2>关联能力</h2>
        <p className="muted">
          由 expert.yaml 的 skillCompatibility / preloadSkills 解析；skillCompatibility 优先匹配 MCP 连接器，否则匹配技能
        </p>
      </header>
      <CapabilityList
        title={`推荐${TERM.skill}`}
        hint="preloadSkills + skillCompatibility 中解析为技能的条目"
        items={capabilities.skills}
        kind="skill"
        onOpenSkill={onOpenSkill}
      />
      <CapabilityList
        title={`推荐${TERM.connector}（${TERM.runtimeMcp}）`}
        hint="skillCompatibility 中解析为 MCP 连接器的条目"
        items={capabilities.connectors}
        kind="connector"
        onOpenRuntime={onOpenRuntime}
      />
      <CapabilityList
        title="未解析 ID"
        hint="在技能与连接器 registry 中均未找到"
        items={capabilities.unresolved}
        kind="skill"
      />
    </section>
  );
}
