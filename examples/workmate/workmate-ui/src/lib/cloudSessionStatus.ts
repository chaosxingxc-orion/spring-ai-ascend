/** Maps cloud session status to a CSS status class suffix. */
export function cloudStatusClass(status: string): string {
  return status.toLowerCase().replace(/_/g, '-');
}

export function cloudStatusLabel(status: string): string {
  switch (status.toUpperCase()) {
    case 'RUNNING':
      return '运行中';
    case 'SLEEPING':
      return '休眠';
    case 'FAILED':
      return '失败';
    case 'DESTROYED':
      return '已销毁';
    case 'PROVISIONING':
      return '启动中';
    default:
      return status;
  }
}
