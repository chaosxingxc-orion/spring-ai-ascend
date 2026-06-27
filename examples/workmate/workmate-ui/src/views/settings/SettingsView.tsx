import { NavLink } from 'react-router-dom';
import { useSettings } from '../../features/settings/SettingsProvider';
import { t } from '../../lib/i18n';
import type { SettingsSection } from '../../features/settings/settingsTypes';
import { MemorySettingsPanel } from './MemorySettingsPanel';
import { SecurityCenterPanel } from './SecurityCenterPanel';
import { DataManagementPanel } from './DataManagementPanel';
import { IdleCapabilitiesPanel } from './IdleCapabilitiesPanel';
import { TenantQuotaPanel } from './TenantQuotaPanel';
import { settingsPath } from '../../lib/paths';

const SECTIONS: SettingsSection[] = ['general', 'memory', 'security', 'data', 'quota', 'about'];

function sectionLabel(section: SettingsSection): string {
  switch (section) {
    case 'general':
      return t('settings.general');
    case 'memory':
      return t('settings.memory');
    case 'security':
      return t('settings.security');
    case 'data':
      return '数据管理';
    case 'quota':
      return '企业配额';
    case 'about':
      return t('settings.about');
  }
}

interface SettingsViewProps {
  section: SettingsSection;
  onOpenAudit?: () => void;
}

export function SettingsView({ section, onOpenAudit }: SettingsViewProps) {
  const { settings, updateSettings } = useSettings();

  return (
    <main className="settings-page">
      <header className="settings-header">
        <h1>{t('settings.title')}</h1>
      </header>

      <div className="settings-layout">
        <nav className="settings-nav" aria-label={t('settings.title')}>
          {SECTIONS.map((item) => (
            <NavLink
              key={item}
              to={settingsPath(item)}
              end={item === 'general'}
              className={({ isActive }) =>
                `settings-nav-item${isActive ? ' active' : ''}`
              }
            >
              {sectionLabel(item)}
            </NavLink>
          ))}
        </nav>

        <div className="settings-content">
          {section === 'general' && (
            <section className="settings-panel">
              <h2>{t('settings.general')}</h2>

              <label className="settings-field">
                <span className="settings-field-label">{t('settings.language')}</span>
                <select
                  value={settings.language}
                  onChange={(event) =>
                    updateSettings({ language: event.target.value as 'zh' | 'en' })
                  }
                >
                  <option value="zh">{t('settings.language.zh')}</option>
                  <option value="en">{t('settings.language.en')}</option>
                </select>
              </label>

              <label className="settings-field">
                <span className="settings-field-label">{t('settings.fontSize')}</span>
                <select
                  value={settings.fontSize}
                  onChange={(event) =>
                    updateSettings({
                      fontSize: event.target.value as 'sm' | 'md' | 'lg',
                    })
                  }
                >
                  <option value="sm">{t('settings.fontSize.sm')}</option>
                  <option value="md">{t('settings.fontSize.md')}</option>
                  <option value="lg">{t('settings.fontSize.lg')}</option>
                </select>
              </label>

              <label className="settings-field settings-field-checkbox">
                <input
                  type="checkbox"
                  checked={settings.compactMode}
                  onChange={(event) => updateSettings({ compactMode: event.target.checked })}
                />
                <span>
                  {t('settings.compactMode')}
                  <span className="settings-field-hint">{t('settings.compactMode.hint')}</span>
                </span>
              </label>

              <label className="settings-field settings-field-checkbox">
                <input
                  type="checkbox"
                  checked={settings.autoArchiveOnCreate}
                  onChange={(event) => updateSettings({ autoArchiveOnCreate: event.target.checked })}
                />
                <span>
                  {t('settings.autoArchiveOnCreate')}
                  <span className="settings-field-hint">{t('settings.autoArchiveOnCreate.hint')}</span>
                </span>
              </label>

              <fieldset className="settings-field">
                <legend className="settings-field-label">{t('settings.submitShortcut')}</legend>
                <label className="settings-radio">
                  <input
                    type="radio"
                    name="submitShortcut"
                    checked={settings.submitShortcut === 'enter'}
                    onChange={() => updateSettings({ submitShortcut: 'enter' })}
                  />
                  <span>{t('settings.submitShortcut.enter')}</span>
                </label>
                <label className="settings-radio">
                  <input
                    type="radio"
                    name="submitShortcut"
                    checked={settings.submitShortcut === 'cmdEnter'}
                    onChange={() => updateSettings({ submitShortcut: 'cmdEnter' })}
                  />
                  <span>{t('settings.submitShortcut.cmdEnter')}</span>
                </label>
              </fieldset>
            </section>
          )}

          {section === 'memory' && (
            <section>
              <h2>{t('settings.memory')}</h2>
              <MemorySettingsPanel />
            </section>
          )}

          {section === 'security' && (
            <section>
              <h2>{t('settings.security')}</h2>
              <SecurityCenterPanel onOpenAudit={onOpenAudit} />
            </section>
          )}

          {section === 'data' && (
            <section>
              <h2>数据管理</h2>
              <DataManagementPanel />
              <IdleCapabilitiesPanel />
            </section>
          )}

          {section === 'quota' && (
            <section>
              <h2>企业配额</h2>
              <TenantQuotaPanel />
            </section>
          )}

          {section === 'about' && (
            <section className="settings-panel">
              <h2>{t('settings.about')}</h2>
              <p className="settings-about-title">{t('settings.about.version')}</p>
              <p className="settings-panel-desc">{t('settings.about.desc')}</p>
              <p className="settings-field-hint">v0.3 · Semantic Wrapper</p>
            </section>
          )}
        </div>
      </div>
    </main>
  );
}

export function parseSettingsSection(raw: string | undefined): SettingsSection {
  if (raw === 'memory' || raw === 'security' || raw === 'data' || raw === 'quota' || raw === 'about') {
    return raw;
  }
  return 'general';
}
