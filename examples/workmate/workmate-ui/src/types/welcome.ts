/** Playbook / 最佳实践 / 精选推荐 — 数据来自 office/playbooks/*.yaml */
export interface PlaybookCard {
  id: string;
  title: string;
  description?: string;
  accent?: string;
  expertId?: string;
  initPrompt: string;
  placements?: string[];
}

export interface WelcomeChip {
  label: string;
  icon?: string;
  initPrompt?: string;
}

export interface WelcomeScene {
  id: string;
  label: string;
  icon?: string;
  defaultScene: boolean;
  chips: WelcomeChip[];
}

export interface WelcomeSection {
  title?: string;
  actionLabel?: string;
  placement: string;
  enabled: boolean;
  playbooks: PlaybookCard[];
}

export interface WelcomeSampleTask {
  id: string;
  title: string;
  prompt: string;
  expertId?: string;
}

export interface WelcomeInterestTag {
  id: string;
  label: string;
}

export interface WelcomeOnboardingConfig {
  enabled: boolean;
  step1Title?: string;
  step1Hint?: string;
  step2Title?: string;
  step2Hint?: string;
  step3Title?: string;
  step3Hint?: string;
  interests: WelcomeInterestTag[];
  sampleTasks: WelcomeSampleTask[];
}

export interface WelcomeConfig {
  hero: { headline?: string; title?: string; tagline?: string };
  dock: { placeholderNew?: string; placeholderSession?: string };
  growthPlan: { label?: string; enabled: boolean };
  bestPractices: WelcomeSection;
  marketFeatured: WelcomeSection;
  homeFeatured: { enabled: boolean };
  marketSearchPlaceholder?: string;
  scenes: WelcomeScene[];
  onboarding?: WelcomeOnboardingConfig;
}

export type SceneMode = string;

export function defaultSceneId(config: WelcomeConfig): string {
  const flagged = config.scenes.find((scene) => scene.defaultScene);
  return flagged?.id ?? config.scenes[0]?.id ?? 'working';
}

export function heroDisplayText(config: WelcomeConfig): string {
  if (config.hero.headline?.trim()) {
    return config.hero.headline.trim();
  }
  const title = config.hero.title?.trim() ?? '';
  const tagline = config.hero.tagline?.trim() ?? '';
  return `${title}${tagline}`;
}

export function heroUsesSplitBrand(config: WelcomeConfig): boolean {
  const title = config.hero.title?.trim();
  const tagline = config.hero.tagline?.trim();
  return Boolean(title && tagline);
}

export function chipPrompt(chip: WelcomeChip): string {
  const custom = chip.initPrompt?.trim();
  if (custom) {
    return custom;
  }
  return `帮我完成：${chip.label}`;
}
