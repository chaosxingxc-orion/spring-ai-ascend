import { readFile } from 'node:fs/promises';

export interface RelayedRunEvent {
  seq: number;
  name: string;
  data: Record<string, unknown>;
}

export interface AcpRelayResult {
  ingested: number;
  events: RelayedRunEvent[];
}

function normalizeApiBase(apiBase: string): string {
  return apiBase.replace(/\/$/, '');
}

export async function relayNdjsonString(
  apiBase: string,
  sessionId: string,
  ndjson: string,
): Promise<AcpRelayResult> {
  const body = ndjson.trim();
  if (!body) {
    throw new Error('Empty NDJSON payload');
  }

  const response = await fetch(
    `${normalizeApiBase(apiBase)}/api/v1/sessions/${encodeURIComponent(sessionId)}/acp/ingest/ndjson`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-ndjson' },
      body: `${body}\n`,
    },
  );

  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(detail || `ACP ingest failed: HTTP ${response.status}`);
  }

  const events = (await response.json()) as RelayedRunEvent[];
  return { ingested: events.length, events };
}

export async function relayNdjsonFile(
  apiBase: string,
  sessionId: string,
  filePath: string,
): Promise<AcpRelayResult> {
  const ndjson = await readFile(filePath, 'utf8');
  return relayNdjsonString(apiBase, sessionId, ndjson);
}

/** Extract complete NDJSON / SSE data lines from a growing buffer. */
export function drainSessionUpdateLines(buffer: string): { lines: string[]; rest: string } {
  const lines: string[] = [];
  let rest = buffer;

  while (true) {
    const newlineIndex = rest.indexOf('\n');
    if (newlineIndex < 0) {
      break;
    }
    const raw = rest.slice(0, newlineIndex).trim();
    rest = rest.slice(newlineIndex + 1);
    if (!raw) {
      continue;
    }
    const payload = raw.startsWith('data:') ? raw.slice(5).trim() : raw;
    if (payload && payload !== '[DONE]') {
      lines.push(payload);
    }
  }

  return { lines, rest };
}

/**
 * W38 Phase 3 — connect external streamable-http / SSE sidecar, buffer sessionUpdate lines,
 * then POST to workmate-api `/acp/ingest/ndjson`.
 */
export async function relayStreamableHttpSidecar(
  apiBase: string,
  sessionId: string,
  sidecarUrl: string,
  init?: RequestInit,
): Promise<AcpRelayResult> {
  const url = new URL(sidecarUrl);
  if (!url.searchParams.has('sessionId')) {
    url.searchParams.set('sessionId', sessionId);
  }

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'application/x-ndjson, text/event-stream, application/json',
    },
    ...init,
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(detail || `Sidecar stream failed: HTTP ${response.status}`);
  }

  if (!response.body) {
    throw new Error('Sidecar response has no body');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const collected: string[] = [];
  let pending = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    pending += decoder.decode(value, { stream: true });
    const drained = drainSessionUpdateLines(pending);
    pending = drained.rest;
    collected.push(...drained.lines);
  }

  const tail = pending.trim();
  if (tail) {
    const payload = tail.startsWith('data:') ? tail.slice(5).trim() : tail;
    if (payload && payload !== '[DONE]') {
      collected.push(payload);
    }
  }

  if (collected.length === 0) {
    throw new Error('Sidecar stream returned no sessionUpdate lines');
  }

  return relayNdjsonString(apiBase, sessionId, collected.join('\n'));
}
