import { describe, expect, it } from 'vitest';
import { ACP_META } from './acpMetaKeys';

describe('ACP_META', () => {
  it('uses workmate.ai namespace for member routing keys', () => {
    expect(ACP_META.memberEvent).toBe('workmate.ai/memberEvent');
    expect(ACP_META.memberId).toBe('workmate.ai/memberId');
    expect(ACP_META.parentRunId).toBe('workmate.ai/parentRunId');
    expect(ACP_META.offset).toBe('workmate.ai/offset');
  });
});
