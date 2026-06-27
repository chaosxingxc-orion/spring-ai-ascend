import { describe, expect, it } from 'vitest';
import { cloudStatusClass, cloudStatusLabel } from './cloudSessionStatus';

describe('cloudSessionStatus', () => {
  it('maps status to css class', () => {
    expect(cloudStatusClass('RUNNING')).toBe('running');
    expect(cloudStatusClass('SLEEPING')).toBe('sleeping');
  });

  it('labels status in Chinese', () => {
    expect(cloudStatusLabel('RUNNING')).toBe('运行中');
    expect(cloudStatusLabel('DESTROYED')).toBe('已销毁');
  });
});
