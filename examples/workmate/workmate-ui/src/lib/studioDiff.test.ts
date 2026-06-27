import { describe, expect, it } from 'vitest';
import { studioDiffBaselineLabel, studioDiffFieldLabel } from './studioDiff';

describe('studioDiff', () => {
  it('labels changed fields', () => {
    expect(studioDiffFieldLabel('promptContent')).toBe('Prompt');
    expect(studioDiffFieldLabel('welcomeYaml')).toBe('welcome.yaml');
    expect(studioDiffFieldLabel('unknown')).toBe('unknown');
  });

  it('labels baseline source', () => {
    expect(studioDiffBaselineLabel('BUILTIN')).toBe('内置');
    expect(studioDiffBaselineLabel(null)).toBe('无原始版本');
  });
});
