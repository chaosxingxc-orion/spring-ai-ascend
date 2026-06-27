import { describe, expect, it } from 'vitest';
import { setI18nLanguage, t } from './i18n';

describe('i18n', () => {
  it('returns Chinese by default', () => {
    setI18nLanguage('zh');
    expect(t('settings.title')).toBe('设置');
  });

  it('switches to English', () => {
    setI18nLanguage('en');
    expect(t('settings.title')).toBe('Settings');
    setI18nLanguage('zh');
  });

  it('interpolates params', () => {
    setI18nLanguage('en');
    expect(t('time.minutesAgo', { n: 3 })).toBe('3m ago');
    setI18nLanguage('zh');
  });

  it('includes chat checkpoint and disclaimer keys', () => {
    setI18nLanguage('zh');
    expect(t('chat.checkpointLabel')).toBe('新一轮对话');
    expect(t('chat.aiDisclaimer')).toContain('AI 生成内容');
  });
});
