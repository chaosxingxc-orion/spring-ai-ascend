import type { ToolStatus } from '../types/events';

const LEGACY_STATUS: Record<string, ToolStatus> = {
  running: 'executing',
  done: 'success',
  error: 'failed',
};

export function normalizeToolStatus(raw: string | undefined): ToolStatus | null {
  if (!raw) {
    return null;
  }
  if (raw === 'executing' || raw === 'success' || raw === 'failed' || raw === 'waiting') {
    return raw;
  }
  return LEGACY_STATUS[raw] ?? null;
}

export function statusLabel(status: ToolStatus): string {
  switch (status) {
    case 'executing':
      return '执行中';
    case 'waiting':
      return '待审批';
    case 'success':
      return '已完成';
    case 'failed':
      return '失败';
  }
}

export function isToolInProgress(status: ToolStatus): boolean {
  return status === 'executing' || status === 'waiting';
}

export function isToolFailed(status: ToolStatus): boolean {
  return status === 'failed';
}

export function toolEndStatus(failed: boolean): ToolStatus {
  return failed ? 'failed' : 'success';
}
