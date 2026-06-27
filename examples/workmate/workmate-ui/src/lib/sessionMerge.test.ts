import { describe, expect, it } from 'vitest';
import { mergeSessionSummaries, upsertSession } from './sessionMerge';
import type { Session } from '../types/api';

const base: Session = {
  id: 'a',
  title: 'Task A',
  workspaceRoot: '/data/a',
  workspaceKey: 'default',
  status: 'CREATED',
  permissionMode: 'CRAFT',
  createdAt: '2026-06-25T00:00:00Z',
  updatedAt: '2026-06-25T01:00:00Z',
  promptTokens: 120,
  completionTokens: 80,
};

describe('sessionMerge', () => {
  it('preserves hydrated fields when merging summaries', () => {
    const summary: Session = {
      id: 'a',
      title: 'Renamed',
      workspaceRoot: '',
      workspaceKey: 'default',
      status: 'RUNNING',
      permissionMode: 'CRAFT',
      createdAt: '2026-06-25T00:00:00Z',
      updatedAt: '2026-06-25T02:00:00Z',
    };
    const merged = mergeSessionSummaries([base], [summary])[0];
    expect(merged.title).toBe('Renamed');
    expect(merged.status).toBe('RUNNING');
    expect(merged.workspaceRoot).toBe('/data/a');
    expect(merged.promptTokens).toBe(120);
  });

  it('upserts full session in place', () => {
    const full: Session = {
      ...base,
      modelId: 'deepseek-chat',
      effort: 'HIGH',
    };
    const next = upsertSession([base], full);
    expect(next).toHaveLength(1);
    expect(next[0].modelId).toBe('deepseek-chat');
    expect(next[0].promptTokens).toBe(120);
  });
});
