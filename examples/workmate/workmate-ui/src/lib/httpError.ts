/** Normalize API / fetch error bodies into a short user-facing message. */
export function readErrorMessage(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) {
    return '请求失败';
  }
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const parsed = JSON.parse(trimmed) as Record<string, unknown>;
      if (typeof parsed.message === 'string' && parsed.message.trim()) {
        return parsed.message.trim();
      }
      if (typeof parsed.error === 'string' && parsed.error.trim()) {
        return parsed.error.trim();
      }
    } catch {
      // fall through
    }
  }
  if (/^HTTP \d{3}$/.test(trimmed)) {
    if (trimmed === 'HTTP 409') {
      return '会话正在运行中，请稍后再试';
    }
    return trimmed;
  }
  return trimmed;
}

/** HTTP 4xx from prompt/retry/edit — resume SSE will not recover. */
export function isClientHttpError(message: string): boolean {
  return /HTTP 4\d{2}/.test(message) || message.includes('会话正在运行');
}
