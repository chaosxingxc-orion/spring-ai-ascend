interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
  onDismiss?: () => void;
  variant?: 'error' | 'offline';
  retryLabel?: string;
  dismissLabel?: string;
}

/** G37 — unified error strip with optional retry / dismiss. */
export function ErrorBanner({
  message,
  onRetry,
  onDismiss,
  variant = 'error',
  retryLabel = '重试',
  dismissLabel = '关闭',
}: ErrorBannerProps) {
  return (
    <div
      className={`error-banner${variant === 'offline' ? ' offline' : ''}`}
      role="alert"
      aria-live="assertive"
    >
      <span className="error-banner-message">{message}</span>
      <span className="error-banner-actions">
        {onRetry && (
          <button type="button" className="btn secondary" onClick={onRetry}>
            {retryLabel}
          </button>
        )}
        {onDismiss && (
          <button type="button" className="btn ghost" onClick={onDismiss}>
            {dismissLabel}
          </button>
        )}
      </span>
    </div>
  );
}
