import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { SessionLimitBanner } from './SessionLimitBanner';

const limits = { activeCount: 50, maxActive: 50, autoArchiveOnCreate: true };

describe('SessionLimitBanner', () => {
  it('hides when auto-archive is enabled', () => {
    expect(
      renderToStaticMarkup(
        <SessionLimitBanner
          limits={limits}
          autoArchiveEnabled
          onShowHelp={() => undefined}
        />,
      ),
    ).toBe('');
  });

  it('shows critical hint at limit without auto-archive', () => {
    const html = renderToStaticMarkup(
      <SessionLimitBanner
        limits={limits}
        autoArchiveEnabled={false}
        onShowHelp={() => undefined}
      />,
    );
    expect(html).toContain('活跃任务 50/50');
    expect(html).toContain('如何归档');
  });
});
