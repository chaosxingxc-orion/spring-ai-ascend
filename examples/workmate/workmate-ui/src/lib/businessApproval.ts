import type { ApprovalRequiredPayload, ChatItem } from '../types/events';
import { classifyTool } from './toolKind';
import { parseMcpToolName } from './toolKind';

export type BusinessApprovalField = { label: string; value: string };

const FIELD_LABELS: Record<string, string> = {
  customer: '客户',
  customerName: '客户',
  client: '客户',
  company: '企业',
  companyName: '企业',
  amount: '额度',
  creditAmount: '授信额度',
  limit: '额度',
  operation: '操作',
  action: '操作',
  recipient: '收件人',
  email: '邮箱',
  subject: '主题',
  memo: '备注',
  title: '标题',
};

/** MCP / 业务工具审批 — 主时间线业务确认卡片，Bash 仍走弹窗。 */
export function isBusinessApprovalTool(toolName: string): boolean {
  return classifyTool(toolName) === 'mcp';
}

export function buildApprovalChatItem(payload: ApprovalRequiredPayload): Extract<ChatItem, { kind: 'approval' }> {
  return {
    id: `approval-${payload.approvalId}`,
    kind: 'approval',
    approvalId: payload.approvalId,
    tool: payload.tool,
    risk: payload.risk,
    reason: payload.reason,
    summary: payload.summary,
    args: payload.args,
    status: 'pending',
  };
}

export function sessionHasPendingApproval(
  sessionId: string,
  chatBySession: Record<string, ChatItem[]>,
  pendingBySession: Record<string, ApprovalRequiredPayload>,
): boolean {
  if (pendingBySession[sessionId]) {
    return true;
  }
  return (chatBySession[sessionId] ?? []).some(
    (item) => item.kind === 'approval' && item.status === 'pending',
  );
}

export function businessApprovalOperationLabel(toolName: string, summary?: string): string {
  if (summary?.trim()) {
    return summary.trim();
  }
  const { tool } = parseMcpToolName(toolName);
  if (tool) {
    return tool.replace(/_/g, ' ');
  }
  return toolName;
}

function readArgString(args: Record<string, unknown>, keys: string[]): string | undefined {
  for (const key of keys) {
    const value = args[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
    if (typeof value === 'number' && Number.isFinite(value)) {
      return String(value);
    }
  }
  return undefined;
}

export function businessApprovalFields(
  toolName: string,
  args: Record<string, unknown>,
): BusinessApprovalField[] {
  const fields: BusinessApprovalField[] = [];
  const seen = new Set<string>();

  const operation = readArgString(args, ['operation', 'action', 'type']);
  if (operation) {
    fields.push({ label: '操作', value: operation });
    seen.add('operation');
    seen.add('action');
    seen.add('type');
  }

  for (const [key, label] of Object.entries(FIELD_LABELS)) {
    if (seen.has(key)) {
      continue;
    }
    const value = readArgString(args, [key]);
    if (value) {
      fields.push({ label, value });
      seen.add(key);
    }
  }

  if (fields.length === 0) {
    for (const [key, value] of Object.entries(args)) {
      if (typeof value === 'string' && value.trim() && !key.startsWith('_')) {
        const displayLabel = FIELD_LABELS[key] ?? key;
        fields.push({ label: displayLabel, value: value.trim() });
        if (fields.length >= 4) {
          break;
        }
      }
    }
  }

  if (fields.length === 0) {
    fields.push({ label: '工具', value: businessApprovalOperationLabel(toolName) });
  }

  return fields;
}
