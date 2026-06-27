import type { SseEvent } from '../types/events';

export function parseSsePart(part: string): SseEvent | null {
  if (!part) {
    return null;
  }

  let name = 'message';
  let id: string | undefined;
  const dataLines: string[] = [];

  for (const line of part.split('\n')) {
    if (line.startsWith('event:')) {
      name = line.slice(6).trim();
    } else if (line.startsWith('id:')) {
      id = line.slice(3).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  const raw = dataLines.join('\n');
  let data: unknown = raw;
  try {
    data = JSON.parse(raw);
  } catch {
    // keep raw string
  }

  return { name, data, id };
}

export function consumeSseBuffer(
  buffer: string,
  onEvent: (event: SseEvent) => void,
): string {
  const parts = buffer.split('\n\n');
  const remainder = parts.pop() ?? '';

  for (const chunk of parts) {
    const event = parseSsePart(chunk.trim());
    if (event) {
      onEvent(event);
    }
  }

  return remainder;
}
