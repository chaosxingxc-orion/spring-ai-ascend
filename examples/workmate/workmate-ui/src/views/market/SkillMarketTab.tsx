import { useEffect, useMemo, useState } from 'react';
import { installSkill, listSkills, scanSkillSecurity, uninstallSkill } from '../../api/market';
import type { SkillInfo } from '../../types/market';
import { TERM } from '../../lib/terminology';
import { SkillMarketCard } from './SkillMarketCard';

export function SkillMarketTab({
  refreshKey = 0,
  initialSkills,
  onSkillsChange,
}: {
  refreshKey?: number;
  initialSkills?: SkillInfo[];
  onSkillsChange?: (skills: SkillInfo[]) => void;
}) {
  const [skills, setSkills] = useState<SkillInfo[]>(initialSkills ?? []);
  const [loading, setLoading] = useState((initialSkills?.length ?? 0) === 0);
  const [error, setError] = useState<string | null>(null);
  const [subTab, setSubTab] = useState<'store' | 'installed'>('store');
  const [query, setQuery] = useState('');
  const [busyId, setBusyId] = useState<string | null>(null);

  const applySkills = (next: SkillInfo[]) => {
    setSkills(next);
    onSkillsChange?.(next);
  };

  const load = (showSpinner = skills.length === 0) => {
    setError(null);
    if (showSpinner) {
      setLoading(true);
    }
    void listSkills()
      .then(applySkills)
      .catch((err) => setError((err as Error).message))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load((initialSkills?.length ?? 0) === 0);
  }, [refreshKey]);

  useEffect(() => {
    if (initialSkills?.length) {
      setSkills(initialSkills);
      setLoading(false);
    }
  }, [initialSkills]);

  const installedCount = skills.filter((s) => s.installed).length;

  const isFeaturedSkill = (skill: SkillInfo) =>
    skill.source === 'recommended' || skill.tags?.includes('featured');

  const featuredSkills = useMemo(() => skills.filter(isFeaturedSkill), [skills]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let items = subTab === 'installed' ? skills.filter((s) => s.installed) : skills;
    if (q) {
      items = items.filter(
        (skill) =>
          skill.name.toLowerCase().includes(q) ||
          skill.id.toLowerCase().includes(q) ||
          skill.description.toLowerCase().includes(q),
      );
    }
    return items;
  }, [query, skills, subTab]);

  const gridSkills = useMemo(() => {
    if (subTab !== 'store' || featuredSkills.length === 0) {
      return filtered;
    }
    const featuredIds = new Set(featuredSkills.map((skill) => skill.id));
    return filtered.filter((skill) => !featuredIds.has(skill.id));
  }, [filtered, featuredSkills, subTab]);

  const handleInstall = async (skillId: string) => {
    setBusyId(skillId);
    setError(null);
    try {
      const skill = skills.find((item) => item.id === skillId);
      const scan = await scanSkillSecurity(skillId);
      if (!scan.safe) {
        const detail = scan.warnings.join('、');
        const proceed = window.confirm(
          `技能「${skill?.name ?? skillId}」安全扫描发现风险：${detail}\n\n仍要安装吗？`,
        );
        if (!proceed) {
          return;
        }
      } else {
        const proceed = window.confirm(
          `确定安装技能「${skill?.name ?? skillId}」？\n\n安装后将加入运行时技能列表，专家可在任务中通过 / 调用。`,
        );
        if (!proceed) {
          return;
        }
      }
      const updated = await installSkill(skillId);
      applySkills(skills.map((s) => (s.id === updated.id ? updated : s)));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const handleUninstall = async (skill: SkillInfo) => {
    if (skill.policyLocked) {
      return;
    }
    const proceed = window.confirm(
      `确定卸载技能「${skill.name}」？\n\n卸载后任务中将无法再通过 / 调用该技能。`,
    );
    if (!proceed) {
      return;
    }
    setBusyId(skill.id);
    setError(null);
    try {
      const updated = await uninstallSkill(skill.id);
      applySkills(skills.map((s) => (s.id === updated.id ? updated : s)));
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusyId(null);
    }
  };

  const renderCard = (skill: SkillInfo, variant: 'grid' | 'featured') => (
    <SkillMarketCard
      key={skill.id}
      skill={skill}
      busy={busyId === skill.id}
      variant={variant}
      showFavorite={variant === 'grid'}
      onInstall={() => void handleInstall(skill.id)}
      onUninstall={skill.installed ? () => void handleUninstall(skill) : undefined}
    />
  );

  return (
    <div className="market-tab-body">
      <p className="market-tab-intro">
        {TERM.skill}市场 · 可安装的能力包，专家在任务中通过 <kbd>/</kbd> 调用
      </p>
      <div className="market-toolbar">
        <input
          type="search"
          className="market-search"
          placeholder="搜索技能"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      <div className="market-subtabs">
        <button
          type="button"
          className={`market-pill${subTab === 'store' ? ' active' : ''}`}
          onClick={() => setSubTab('store')}
        >
          技能市场
        </button>
        <button
          type="button"
          className={`market-pill${subTab === 'installed' ? ' active' : ''}`}
          onClick={() => setSubTab('installed')}
        >
          已安装 {installedCount}
        </button>
      </div>

      {error && <p className="market-hint error">{error}</p>}
      {loading && <p className="market-hint">加载中…</p>}
      {!loading && gridSkills.length === 0 && featuredSkills.length === 0 && (
        <p className="market-empty">
          {subTab === 'installed' ? '暂无已安装技能。' : '暂无技能。请检查 office/skills 目录。'}
        </p>
      )}

      {subTab === 'store' && featuredSkills.length > 0 && (
        <section className="skill-featured-section" aria-label="推荐技能">
          <div className="skill-featured-header">
            <h2>推荐技能</h2>
            <span className="market-hint">精选 {TERM.runtimeSkills}</span>
          </div>
          <div className="skill-featured-scroll">
            {featuredSkills.map((skill) => renderCard(skill, 'featured'))}
          </div>
        </section>
      )}

      <div className="skill-grid">{gridSkills.map((skill) => renderCard(skill, 'grid'))}</div>
    </div>
  );
}
