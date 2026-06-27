import { describe, expect, it } from 'vitest';
import { walkthroughForAuthMethod } from './connectorOAuthWalkthrough';

describe('walkthroughForAuthMethod', () => {
  it('returns device code steps for qieman-style auth', () => {
    const steps = walkthroughForAuthMethod('DEVICE_CODE');
    expect(steps[0]?.title).toBe('了解连接器');
    expect(steps.some((s) => s.id === 'authorize')).toBe(true);
  });

  it('returns redirect steps', () => {
    const steps = walkthroughForAuthMethod('REDIRECT');
    expect(steps.some((s) => s.title.includes('授权页'))).toBe(true);
  });
});
