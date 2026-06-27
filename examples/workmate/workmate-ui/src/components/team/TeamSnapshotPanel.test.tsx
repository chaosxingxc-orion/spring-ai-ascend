import { describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { TeamSnapshotPanel } from './TeamSnapshotPanel';

vi.mock('../../api/client', () => ({
  getTeamSnapshot: vi.fn(() => Promise.resolve({
    teamId: 'content-bus-team',
    teamName: 'Bus Team',
    description: 'Message bus team',
    pattern: 'message-bus',
    collaboration: 'parallel',
    teamPromptSummary: 'Coordinate via ingress topic.',
    lead: null,
    members: [{
      memberId: 'content-writer',
      name: '文笔佳',
      expertId: 'content-writer',
      role: '撰写专家',
      order: 1,
      avatar: '📝',
      promptSummary: 'Write concise marketing copy.',
      backendType: 'remote',
      subscriptions: ['ingress'],
    }],
    source: 'expert-descriptor+run-events',
  })),
}));

describe('TeamSnapshotPanel', () => {
  it('renders collapsible team snapshot section', () => {
    const html = renderToStaticMarkup(<TeamSnapshotPanel sessionId="sess-1" />);
    expect(html).toContain('团队快照');
    expect(html).toContain('aria-label="团队快照"');
  });
});
