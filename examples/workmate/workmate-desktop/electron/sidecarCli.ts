#!/usr/bin/env node
/**
 * Node CLI mirror of `scripts/dogfood/acp-sidecar-relay.sh`.
 *
 * Usage:
 *   node dist/electron/sidecarCli.js <session-id> [ndjson-file]
 *   WORKMATE_ACP_SIDECAR_URL=http://host/stream node dist/electron/sidecarCli.js <session-id>
 */
import { relayNdjsonFile, relayNdjsonString, relayStreamableHttpSidecar } from './acpSidecarRelay';

async function main(): Promise<void> {
  const sessionId = process.argv[2];
  const ndjsonFile = process.argv[3];
  const apiBase = process.env.WORKMATE_API_URL ?? 'http://127.0.0.1:8080';
  const sidecarUrl = process.env.WORKMATE_ACP_SIDECAR_URL;

  if (!sessionId) {
    console.error('Usage: sidecarCli <session-id> [ndjson-file]');
    console.error('  or set WORKMATE_ACP_SIDECAR_URL for streamable-http relay');
    process.exit(1);
  }

  let result;
  if (sidecarUrl) {
    result = await relayStreamableHttpSidecar(apiBase, sessionId, sidecarUrl);
  } else if (ndjsonFile) {
    result = await relayNdjsonFile(apiBase, sessionId, ndjsonFile);
  } else if (!process.stdin.isTTY) {
    const chunks: Buffer[] = [];
    for await (const chunk of process.stdin) {
      chunks.push(Buffer.from(chunk));
    }
    result = await relayNdjsonString(apiBase, sessionId, Buffer.concat(chunks).toString('utf8'));
  } else {
    console.error('Pass an ndjson file, pipe stdin, or set WORKMATE_ACP_SIDECAR_URL');
    process.exit(1);
  }

  console.log(`ingested ${result.ingested} run_event(s)`);
  for (const row of result.events) {
    console.log(`  seq=${row.seq} name=${row.name}`);
  }
}

main().catch((error: unknown) => {
  console.error((error as Error).message);
  process.exit(1);
});
