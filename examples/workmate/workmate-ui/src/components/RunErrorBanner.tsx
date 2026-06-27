import { ErrorBanner } from './ErrorBanner';

interface RunErrorBannerProps {
  message: string;
  canRetry: boolean;
  onRetry?: () => void;
  onDismiss: () => void;
}

/** Session-scoped run / SSE failure banner (G37). */
export function RunErrorBanner({ message, canRetry, onRetry, onDismiss }: RunErrorBannerProps) {
  return (
    <ErrorBanner
      message={message}
      onRetry={canRetry ? onRetry : undefined}
      onDismiss={onDismiss}
    />
  );
}
