import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { drainSessionUpdateLines } from './acpSidecarRelay';

describe('drainSessionUpdateLines', () => {
  it('parses plain NDJSON lines', () => {
    const drained = drainSessionUpdateLines(
      '{"sessionUpdate":"agent_message_chunk"}\n{"sessionUpdate":"done"}\n',
    );
    assert.equal(drained.lines.length, 2);
    assert.equal(drained.rest, '');
  });

  it('parses SSE data lines', () => {
    const drained = drainSessionUpdateLines(
      'data: {"sessionUpdate":"tool_call"}\n\ndata: [DONE]\n\n',
    );
    assert.equal(drained.lines.length, 1);
  });

  it('keeps partial line in buffer', () => {
    const drained = drainSessionUpdateLines('{"sessionUpdate":"chunk"}\n{"partial":');
    assert.equal(drained.lines.length, 1);
    assert.equal(drained.rest, '{"partial":');
  });
});
