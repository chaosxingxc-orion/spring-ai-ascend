import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { ScrollToBottomButton } from './ScrollToBottomButton';
import { LoadingTips } from './LoadingTips';

describe('ScrollToBottomButton', () => {
  it('renders when visible', () => {
    const html = renderToStaticMarkup(<ScrollToBottomButton visible onClick={() => undefined} />);
    expect(html).toContain('回到底部');
  });

  it('renders nothing when hidden', () => {
    expect(renderToStaticMarkup(<ScrollToBottomButton visible={false} onClick={() => undefined} />)).toBe('');
  });
});

describe('LoadingTips', () => {
  it('shows tip while streaming', () => {
    const html = renderToStaticMarkup(<LoadingTips streaming />);
    expect(html).toContain('不再提示');
  });

  it('shows stage tip when streamStage is set', () => {
    const html = renderToStaticMarkup(<LoadingTips streaming streamStage="tool" />);
    expect(html).toContain('工具');
  });

  it('hides when not streaming', () => {
    expect(renderToStaticMarkup(<LoadingTips streaming={false} />)).toBe('');
  });
});
