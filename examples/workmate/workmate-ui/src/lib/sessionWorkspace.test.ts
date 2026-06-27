import { describe, expect, it } from 'vitest';
import type { Session } from '../types/api';
import type { WorkspacePreset } from '../types/workspace';
import {
  filterSessions,
  groupSessionsByWorkspace,
  isDefaultSessionWorkspaceKey,
  workspaceLabelForSession,
} from './sessionWorkspace';

const presets: WorkspacePreset[] = [
  { id: 'default', name: '默认工作空间', path: '', description: '' },
  { id: 'office-demo', name: '办公示例', path: 'presets/office-demo', description: '' },
];

const sessions: Session[] = [
  {
    id: 'a',
    title: 'Fund task',
    workspaceRoot: '/w/uuid-a',
    workspaceKey: '550e8400-e29b-41d4-a716-446655440000',
    status: 'COMPLETED',
    expertId: 'fund-analyst',
    permissionMode: 'CRAFT',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  },
  {
    id: 'b',
    title: 'Office shared',
    workspaceRoot: '/w/presets/office-demo',
    workspaceKey: 'presets/office-demo',
    status: 'CREATED',
    permissionMode: 'CRAFT',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-03T00:00:00Z',
  },
];

describe('sessionWorkspace', () => {
  it('detects default session workspace keys', () => {
    expect(isDefaultSessionWorkspaceKey('550e8400-e29b-41d4-a716-446655440000')).toBe(true);
    expect(isDefaultSessionWorkspaceKey('presets/office-demo')).toBe(false);
  });

  it('filters sessions by title', () => {
    expect(filterSessions(sessions, [], 'office').map((s) => s.id)).toEqual(['b']);
  });

  it('groups shared workspace sessions', () => {
    const groups = groupSessionsByWorkspace(sessions, presets);
    expect(groups).toHaveLength(1);
    expect(groups[0].preset.id).toBe('office-demo');
    expect(groups[0].sessions.map((s) => s.id)).toEqual(['b']);
  });

  it('labels workspace for session', () => {
    expect(workspaceLabelForSession(sessions[1], presets)).toBe('办公示例');
    expect(workspaceLabelForSession(sessions[0], presets)).toBe('默认工作空间');
  });
});
