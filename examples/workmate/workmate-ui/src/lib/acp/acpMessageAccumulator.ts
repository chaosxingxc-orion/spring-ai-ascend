import type { AcpSessionUpdate } from './runEventToAcp';
import type { RunEventDraft } from './acpTypes';
import { acpToRunEvent } from './acpToRunEvent';

/** W38 Phase 2 — merge consecutive agent_message_chunk before emitting message.delta. */
export class AcpMessageAccumulator {
  private chunkBuffer = '';

  private chunkMeta: Record<string, unknown> = {};

  ingest(update: AcpSessionUpdate | Record<string, unknown>): RunEventDraft[] {
    const sessionUpdate =
      typeof update === 'object' && update && 'sessionUpdate' in update
        ? String(update.sessionUpdate)
        : '';
    if (sessionUpdate === 'agent_message_chunk') {
      const content =
        typeof update === 'object' && update && 'content' in update && update.content
          ? (update.content as Record<string, unknown>)
          : {};
      const text = typeof content.text === 'string' ? content.text : '';
      this.chunkBuffer += text;
      const meta =
        typeof update === 'object' && update && '_meta' in update && update._meta
          ? (update._meta as Record<string, unknown>)
          : {};
      if (Object.keys(meta).length > 0) {
        this.chunkMeta = meta;
      }
      return [];
    }
    const out = this.flush();
    const draft = acpToRunEvent(update as AcpSessionUpdate);
    return draft ? [...out, draft] : out;
  }

  flush(): RunEventDraft[] {
    if (!this.chunkBuffer) {
      return [];
    }
    const draft = acpToRunEvent({
      sessionUpdate: 'agent_message_chunk',
      content: { text: this.chunkBuffer },
      _meta: this.chunkMeta,
    });
    this.chunkBuffer = '';
    this.chunkMeta = {};
    return draft ? [draft] : [];
  }

  ingestAll(updates: AcpSessionUpdate[]): RunEventDraft[] {
    const out: RunEventDraft[] = [];
    for (const update of updates) {
      out.push(...this.ingest(update));
    }
    out.push(...this.flush());
    return out;
  }
}

export function accumulateAcpStream(updates: AcpSessionUpdate[]): RunEventDraft[] {
  return new AcpMessageAccumulator().ingestAll(updates);
}
