import { ingestAcpNdjson } from '../api/client';
import { getDesktopBridge } from './desktopBridge';

export interface AcpSidecarImportResult {
  ingested: number;
}

/** POST NDJSON → `/acp/ingest/ndjson`（Web fetch；Desktop 可走 preload relay）。 */
export async function importAcpNdjsonText(
  sessionId: string,
  ndjson: string,
): Promise<AcpSidecarImportResult> {
  const body = ndjson.trim();
  if (!body) {
    throw new Error('NDJSON 内容为空');
  }

  const bridge = getDesktopBridge();
  if (bridge?.relayAcpNdjson) {
    const result = await bridge.relayAcpNdjson(sessionId, body);
    return { ingested: result.ingested };
  }

  const events = await ingestAcpNdjson(sessionId, `${body}\n`);
  return { ingested: events.length };
}

export async function importAcpNdjsonFile(
  sessionId: string,
  file: File,
): Promise<AcpSidecarImportResult> {
  const text = await file.text();
  return importAcpNdjsonText(sessionId, text);
}

export function canRelayStreamableSidecar(): boolean {
  return Boolean(getDesktopBridge()?.relayStreamableHttp);
}

/** Desktop only — GET streamable-http / SSE sidecar → ingest。 */
export async function relayStreamableSidecar(
  sessionId: string,
  sidecarUrl?: string,
): Promise<AcpSidecarImportResult> {
  const bridge = getDesktopBridge();
  if (!bridge?.relayStreamableHttp) {
    throw new Error('当前环境不支持 streamable-http sidecar');
  }
  const result = await bridge.relayStreamableHttp(sessionId, sidecarUrl);
  return { ingested: result.ingested };
}
