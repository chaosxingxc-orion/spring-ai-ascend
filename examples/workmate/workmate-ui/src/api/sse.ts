import type { SseEvent } from '../types/events';
import { consumeSseBuffer } from '../lib/sseParser';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

import type { MentionRef } from '../types/mention';
import type { UserAttachment } from '../types/events';

export interface PromptStreamOptions {
  sessionId: string;
  message: string;
  mentions?: MentionRef[];
  attachments?: UserAttachment[];
  signal?: AbortSignal;
  onEvent: (event: SseEvent) => void;
  onLastEventId?: (id: string) => void;
}

export interface ResumeStreamOptions {
  sessionId: string;
  lastEventId?: string;
  signal?: AbortSignal;
  onEvent: (event: SseEvent) => void;
  onLastEventId?: (id: string) => void;
}

async function consumeReadableStream(
  response: Response,
  options: Pick<ResumeStreamOptions, 'signal' | 'onEvent' | 'onLastEventId'>,
): Promise<void> {
  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('Response body is not readable');
  }

  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    if (options.signal?.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    buffer = consumeSseBuffer(buffer, (event) => {
      if (event.name === 'heartbeat') {
        return;
      }
      if (event.id) {
        options.onLastEventId?.(event.id);
      }
      options.onEvent(event);
    });
  }

  consumeSseBuffer(`${buffer}\n\n`, (event) => {
    if (event.name === 'heartbeat') {
      return;
    }
    if (event.id) {
      options.onLastEventId?.(event.id);
    }
    options.onEvent(event);
  });
}

export interface PromptQueuedResult {
  status: 'queued';
  queuePosition: number;
  queueDepth: number;
}

export type PromptStreamOutcome = 'streamed' | PromptQueuedResult;

export async function streamPrompt(options: PromptStreamOptions): Promise<PromptStreamOutcome> {
  const body: Record<string, unknown> = { message: options.message };
  if (options.mentions?.length) {
    body.mentions = options.mentions.map((mention) => ({
      type: mention.type,
      id: mention.id,
      path: mention.path,
      label: mention.label,
    }));
  }
  if (options.attachments?.length) {
    body.attachments = options.attachments.map((attachment) => ({
      path: attachment.path,
      name: attachment.name,
      mime: attachment.mime,
    }));
  }
  const response = await fetch(`${API_BASE}/api/v1/sessions/${options.sessionId}/prompt`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json',
    },
    body: JSON.stringify(body),
    signal: options.signal,
  });

  if (response.status === 202) {
    const payload = (await response.json()) as {
      queuePosition?: number;
      queueDepth?: number;
    };
    return {
      status: 'queued',
      queuePosition: payload.queuePosition ?? 1,
      queueDepth: payload.queueDepth ?? payload.queuePosition ?? 1,
    };
  }

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }

  await consumeReadableStream(response, options);
  return 'streamed';
}

export async function streamEditMessage(
  sessionId: string,
  seq: number,
  message: string,
  options: Omit<PromptStreamOptions, 'sessionId' | 'message'>,
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/v1/sessions/${sessionId}/messages/${seq}/edit`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message }),
    signal: options.signal,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }

  await consumeReadableStream(response, options);
}

export async function streamRetry(
  sessionId: string,
  options: Omit<PromptStreamOptions, 'sessionId' | 'message'>,
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/v1/sessions/${sessionId}/retry`, {
    method: 'POST',
    headers: { Accept: 'text/event-stream' },
    signal: options.signal,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }

  await consumeReadableStream(response, options);
}

export async function resumeEventStream(options: ResumeStreamOptions): Promise<void> {
  const headers: Record<string, string> = { Accept: 'text/event-stream' };
  if (options.lastEventId) {
    headers['Last-Event-ID'] = options.lastEventId;
  }

  const response = await fetch(`${API_BASE}/api/v1/sessions/${options.sessionId}/events/stream`, {
    method: 'GET',
    headers,
    signal: options.signal,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }

  await consumeReadableStream(response, options);
}

export async function checkApiHealth(): Promise<boolean> {
  try {
    // Liveness only — full /actuator/health runs audit-chain verify (~seconds on large ledgers).
    const response = await fetch(`${API_BASE}/actuator/health/liveness`);
    if (!response.ok) {
      return false;
    }
    const contentType = response.headers.get('content-type') ?? '';
    if (!contentType.includes('json')) {
      return false;
    }
    const body = (await response.json()) as { status?: string };
    return body.status === 'UP';
  } catch {
    return false;
  }
}
