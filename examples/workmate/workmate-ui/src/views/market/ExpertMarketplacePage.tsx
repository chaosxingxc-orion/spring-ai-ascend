import { useState } from 'react';
import { listSkills } from '../../api/market';
import type { Expert } from '../../types/api';
import type { ConnectorInfo, MarketTab, SkillInfo } from '../../types/market';
import type { PlaybookCard, WelcomeSection } from '../../types/welcome';
import { TERM } from '../../lib/terminology';
import { ConnectorMarketTab } from './ConnectorMarketTab';
import { ExpertDetailDrawer } from './ExpertDetailDrawer';
import { ExpertImportModal } from './ExpertImportModal';
import { ExpertMarketTab, type ExpertSort } from './ExpertMarketTab';
import type { ExpertMarketKind } from '../../lib/expertMarketFilter';
import { SkillMarketTab } from './SkillMarketTab';
import { SkillUploadModal } from './SkillUploadModal';

const MARKET_TABS: { id: MarketTab; label: string }[] = [
  { id: 'experts', label: TERM.expert },
  { id: 'skills', label: TERM.skill },
  { id: 'connectors', label: TERM.connector },
];

interface ExpertMarketplacePageProps {
  tab: MarketTab;
  experts: Expert[];
  expertsLoaded?: boolean;
  marketFeatured: WelcomeSection;
  expertQuery: string;
  expertCategory: string;
  expertKind: ExpertMarketKind;
  expertSort: ExpertSort;
  detailExpert: Expert | null;
  summonBusy: boolean;
  onExpertQueryChange: (query: string) => void;
  onExpertCategoryChange: (category: string) => void;
  onExpertKindChange: (kind: ExpertMarketKind) => void;
  onExpertSortChange: (sort: ExpertSort) => void;
  onTabChange: (tab: MarketTab) => void;
  onBack: () => void;
  onSelectExpert: (expert: Expert) => void;
  onCloseDetail: () => void;
  onRequestSummon: (expert: Expert) => void;
  onPlaybookSelect: (playbook: PlaybookCard) => void;
  marketSearchPlaceholder?: string;
  onExpertsRefresh?: () => void;
  marketSkills?: SkillInfo[];
  marketConnectors?: ConnectorInfo[];
  onMarketSkillsChange?: (skills: SkillInfo[]) => void;
  onMarketConnectorsChange?: (connectors: ConnectorInfo[]) => void;
}

export function ExpertMarketplacePage({
  tab,
  experts,
  expertsLoaded = true,
  marketFeatured,
  expertQuery,
  expertCategory,
  expertKind,
  expertSort,
  detailExpert,
  onExpertQueryChange,
  onExpertCategoryChange,
  onExpertKindChange,
  onExpertSortChange,
  onTabChange,
  onBack,
  onSelectExpert,
  onCloseDetail,
  onRequestSummon,
  onPlaybookSelect,
  marketSearchPlaceholder,
  onExpertsRefresh,
  marketSkills,
  marketConnectors,
  onMarketSkillsChange,
  onMarketConnectorsChange,
}: ExpertMarketplacePageProps) {
  const [expertImportOpen, setExpertImportOpen] = useState(false);
  const [skillUploadOpen, setSkillUploadOpen] = useState(false);
  const [skillRefreshKey, setSkillRefreshKey] = useState(0);

  return (
    <main className="market-page">
      <header className="market-page-header">
        <button type="button" className="btn ghost market-back" onClick={onBack}>
          ← 返回任务
        </button>
        <nav className="market-tabs" aria-label="能力市场">
          {MARKET_TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`market-tab${tab === item.id ? ' active' : ''}`}
              onClick={() => onTabChange(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </header>

      <div className="market-tab-panels">
        <div className={`market-tab-panel${tab === 'experts' ? ' is-active' : ''}`}>
          <ExpertMarketTab
            experts={experts}
            loaded={expertsLoaded}
            query={expertQuery}
            category={expertCategory}
            kind={expertKind}
            sort={expertSort}
            featuredSection={marketFeatured}
            onCategoryChange={onExpertCategoryChange}
            onKindChange={onExpertKindChange}
            onSortChange={onExpertSortChange}
            onSelectExpert={onSelectExpert}
            onSummonExpert={onRequestSummon}
            onPlaybookSelect={onPlaybookSelect}
            onOpenImport={() => setExpertImportOpen(true)}
            searchPlaceholder={marketSearchPlaceholder}
            onQueryChange={onExpertQueryChange}
          />
        </div>
        <div className={`market-tab-panel${tab === 'skills' ? ' is-active' : ''}`}>
          <SkillMarketTab
            refreshKey={skillRefreshKey}
            initialSkills={marketSkills}
            onSkillsChange={onMarketSkillsChange}
            onOpenUpload={() => setSkillUploadOpen(true)}
          />
        </div>
        <div className={`market-tab-panel${tab === 'connectors' ? ' is-active' : ''}`}>
          <ConnectorMarketTab
            initialConnectors={marketConnectors}
            onConnectorsChange={onMarketConnectorsChange}
          />
        </div>
      </div>

      <ExpertDetailDrawer
        expert={detailExpert}
        onClose={onCloseDetail}
        onSummon={(expert) => {
          onCloseDetail();
          onRequestSummon(expert);
        }}
      />
      <ExpertImportModal
        open={expertImportOpen}
        onClose={() => setExpertImportOpen(false)}
        onImported={() => {
          onExpertsRefresh?.();
        }}
      />
      <SkillUploadModal
        open={skillUploadOpen}
        onClose={() => setSkillUploadOpen(false)}
        onUploaded={() => {
          setSkillRefreshKey((key) => key + 1);
          void listSkills()
            .then((items) => onMarketSkillsChange?.(items))
            .catch(() => undefined);
        }}
      />
    </main>
  );
}
