import { describe, expect, it } from 'vitest';
import { parseAcpNdjson, sessionUpdatesToNdjson } from './acpNdjson';

describe('acpNdjson', () => {
  it('round-trips sessionUpdate rows', () => {
    const updates = [
      { sessionUpdate: 'agent_message_chunk', content: { text: 'hel' } },
      { sessionUpdate: 'agent_message_chunk', content: { text: 'lo' } },
    ];
    const ndjson = sessionUpdatesToNdjson(updates);
    expect(parseAcpNdjson(ndjson)).toEqual(updates);
  });

  it('skips blank lines when parsing', () => {
    const rows = parseAcpNdjson('\n{"sessionUpdate":"user","content":{"text":"hi"}}\n');
    expect(rows).toHaveLength(1);
    expect(rows[0]?.sessionUpdate).toBe('user');
  });
});
