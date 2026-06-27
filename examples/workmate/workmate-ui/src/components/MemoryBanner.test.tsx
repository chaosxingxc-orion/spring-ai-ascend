import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryBanner } from './MemoryBanner';

describe('MemoryBanner', () => {
  it('renders nothing when disabled', () => {
    expect(renderToStaticMarkup(<MemoryBanner enabled={false} injectPreview="hello" />)).toBe('');
  });

  it('shows enabled hint when memory is on but empty', () => {
    const html = renderToStaticMarkup(<MemoryBanner enabled injectPreview="" />);
    expect(html).toContain('记忆已开启');
    expect(html).not.toContain('记忆已加载');
  });

  it('shows collapsed label when memory is loaded', () => {
    const html = renderToStaticMarkup(
      <MemoryBanner enabled injectPreview="Prefers concise answers" />,
    );
    expect(html).toContain('记忆已加载');
    expect(html).not.toContain('Prefers concise answers');
  });

  it('shows remember button when session can be captured', () => {
    const html = renderToStaticMarkup(
      <MemoryBanner enabled canRememberSession onRememberSession={() => undefined} />,
    );
    expect(html).toContain('记住本次对话');
  });

  it('shows view memory button when handler provided', () => {
    const html = renderToStaticMarkup(
      <MemoryBanner enabled onViewMemory={() => undefined} />,
    );
    expect(html).toContain('查看记忆');
  });
});
