import { describe, expect, it } from 'vitest';
import {
  artifactDisplayName,
  findTeamBlackboardPath,
  isTeamBlackboardPath,
} from './artifactDisplay';

describe('artifactDisplay', () => {
  it('detects team blackboard paths', () => {
    expect(isTeamBlackboardPath('team/run-1/blackboard.md')).toBe(true);
    expect(isTeamBlackboardPath('deliverables/report.md')).toBe(false);
  });

  it('finds the deepest blackboard path', () => {
    const paths = [
      'team/run/blackboard.md',
      'team/run/nested/blackboard.md',
      'deliverables/report.md',
    ];
    expect(findTeamBlackboardPath(paths)).toBe('team/run/nested/blackboard.md');
  });

  it('uses friendly display names', () => {
    expect(artifactDisplayName('team/run/blackboard.md', 'blackboard.md')).toBe('团队黑板');
    expect(artifactDisplayName('deliverables/report.md', 'report.md')).toBe('report.md');
  });
});
