import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AutomationJobWizard } from './AutomationJobWizard';

describe('AutomationJobWizard', () => {
  it('renders schedule step with presets', () => {
    const html = renderToStaticMarkup(
      <AutomationJobWizard experts={[]} onSubmit={async () => undefined} />,
    );
    expect(html).toContain('执行计划');
    expect(html).toContain('每天');
    expect(html).toContain('预览：');
  });
});
