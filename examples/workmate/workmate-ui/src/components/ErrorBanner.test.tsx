import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ErrorBanner } from './ErrorBanner';
import { RunErrorBanner } from './RunErrorBanner';

describe('ErrorBanner', () => {
  it('renders message with retry and dismiss', () => {
    const html = renderToStaticMarkup(
      <ErrorBanner message="连接中断" onRetry={() => undefined} onDismiss={() => undefined} />,
    );
    expect(html).toContain('连接中断');
    expect(html).toContain('重试');
    expect(html).toContain('关闭');
    expect(html).toContain('role="alert"');
  });
});

describe('RunErrorBanner', () => {
  it('hides retry when run cannot be retried', () => {
    const html = renderToStaticMarkup(
      <RunErrorBanner message="会话冲突" canRetry={false} onDismiss={() => undefined} />,
    );
    expect(html).toContain('会话冲突');
    expect(html).not.toContain('重试');
  });
});
