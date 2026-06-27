import { ErrorBanner } from './ErrorBanner';

interface ApiBannerProps {
  online: boolean;
  error: string | null;
  onRetry: () => void;
}

export function ApiBanner({ online, error, onRetry }: ApiBannerProps) {
  if (online && !error) {
    return null;
  }

  return (
    <ErrorBanner
      message={
        error ??
        (online
          ? '请求失败'
          : '无法连接 workmate-api — 请先运行 ./scripts/run-local.sh')
      }
      onRetry={onRetry}
      variant={online ? 'error' : 'offline'}
    />
  );
}
