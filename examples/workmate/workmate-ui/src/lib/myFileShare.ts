export function buildMyFileShareLink(origin: string, sessionId: string, filePath: string): string {
  const params = new URLSearchParams({ file: filePath });
  return `${origin}/s/${sessionId}?${params.toString()}`;
}
