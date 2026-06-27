import { describe, expect, it } from 'vitest';
import { LOADING_TIPS, pickLoadingTip } from './loadingTips';

describe('pickLoadingTip', () => {
  it('rotates through tips deterministically', () => {
    expect(pickLoadingTip(0)).toBe(LOADING_TIPS[0]);
    expect(pickLoadingTip(1)).toBe(LOADING_TIPS[1]);
    expect(pickLoadingTip(LOADING_TIPS.length)).toBe(LOADING_TIPS[0]);
  });
});
