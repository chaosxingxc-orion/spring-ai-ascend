import { describe, expect, it } from 'vitest';
import { consumeSseBuffer, parseSsePart } from './sseParser';

describe('parseSsePart', () => {
  it('parses event name and JSON data', () => {
    const event = parseSsePart('event: message.delta\ndata: {"text":"hi"}');
    expect(event).toEqual({ name: 'message.delta', data: { text: 'hi' } });
  });

  it('returns null for empty part', () => {
    expect(parseSsePart('')).toBeNull();
  });
});

describe('consumeSseBuffer', () => {
  it('emits complete frames and keeps remainder', () => {
    const events: string[] = [];
    const remainder = consumeSseBuffer(
      'event: tool.start\ndata: {"toolName":"workmate_write"}\n\nevent: message.delta\ndata: {"text":"a"',
      (e) => events.push(e.name),
    );
    expect(events).toEqual(['tool.start']);
    expect(remainder).toContain('message.delta');
  });
});
