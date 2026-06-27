export type Language = 'zh' | 'en';
export type FontSize = 'sm' | 'md' | 'lg';
export type SubmitShortcut = 'enter' | 'cmdEnter';
export type SettingsSection = 'general' | 'memory' | 'security' | 'data' | 'quota' | 'about';

export interface UserSettings {
  language: Language;
  fontSize: FontSize;
  compactMode: boolean;
  submitShortcut: SubmitShortcut;
  autoArchiveOnCreate: boolean;
}

export const DEFAULT_SETTINGS: UserSettings = {
  language: 'zh',
  fontSize: 'md',
  compactMode: false,
  submitShortcut: 'enter',
  autoArchiveOnCreate: true,
};

export const SETTINGS_STORAGE_KEY = 'workmate.settings.v1';
