import type { ApprovalRequiredPayload, ChatItem } from '../types/events';
import { isBusinessApprovalTool } from './businessApproval';
import { parseApprovalRequired } from './eventPayload';
import type { RunEventRow } from './reasoningHydrate';

function readApprovalId(data: Record<string, unknown> | undefined): string | undefined {
  if (!data) {
    return undefined;
  }
  const raw = data.approvalId ?? data.approval_id;
  return typeof raw === 'string' && raw.trim() ? raw.trim() : undefined;
}

/** Restore modal (non-MCP) approval state from run_events after refresh — no polling. */
export function modalPendingApprovalFromRunEvents(
  events: RunEventRow[],
  chatItems: ChatItem[],
): ApprovalRequiredPayload | null {
  const resolvedIds = new Set<string>();
  for (const item of chatItems) {
    if (item.kind === 'approval' && item.status !== 'pending') {
      resolvedIds.add(item.approvalId);
    }
  }
  for (const event of events) {
    if (event.name === 'approval.decided' || event.name === 'mcp.approval.decided') {
      const id = readApprovalId(event.data);
      if (id) {
        resolvedIds.add(id);
      }
    }
  }

  let latest: ApprovalRequiredPayload | null = null;
  for (const event of events) {
    if (event.name !== 'approval.required' || !event.data) {
      continue;
    }
    const payload = parseApprovalRequired(event.data);
    if (!payload || isBusinessApprovalTool(payload.tool)) {
      continue;
    }
    if (resolvedIds.has(payload.approvalId)) {
      continue;
    }
    latest = payload;
  }
  return latest;
}
