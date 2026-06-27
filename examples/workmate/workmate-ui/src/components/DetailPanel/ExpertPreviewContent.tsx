import type { Expert } from '../../types/api';

interface ExpertPreviewContentProps {
  expert: Expert;
}

function resolveI18n(value: string | Record<string, string> | undefined | null): string {
  if (!value) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  return value.zh ?? value.en ?? Object.values(value)[0] ?? '';
}

function resolvedQuickPrompts(expert: Expert): string[] {
  if (expert.quickPrompts && expert.quickPrompts.length > 0) {
    return expert.quickPrompts;
  }
  if (expert.defaultInitPrompt?.trim()) {
    return [expert.defaultInitPrompt.trim()];
  }
  return [];
}

/** W39-C1 — 专家 Tab 主区：descriptor + quickPrompts + 成员摘要。 */
export function ExpertPreviewContent({ expert }: ExpertPreviewContentProps) {
  const profession = resolveI18n(expert.profession);
  const quickPrompts = resolvedQuickPrompts(expert);
  const isTeam = expert.expertType === 'team';

  return (
    <section className="expert-preview-content" aria-label="专家详情">
      <header className="expert-preview-header">
        <h3>{expert.name}</h3>
        <span className="expert-preview-type">{isTeam ? '专家团' : '单专家'}</span>
      </header>
      {profession && <p className="expert-preview-profession muted">{profession}</p>}
      <p className="expert-preview-description">{expert.description}</p>
      {expert.tags.length > 0 && (
        <ul className="expert-preview-tags">
          {expert.tags.map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      )}
      {isTeam && expert.members && expert.members.length > 0 && (
        <div className="expert-preview-members">
          <h4>成员</h4>
          <ul>
            {expert.members.map((member) => (
              <li key={member.id}>
                <span className="expert-preview-member-name">{member.name}</span>
                {member.role && (
                  <span className="expert-preview-member-role muted">{member.role}</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
      {expert.coordination?.pattern && (
        <p className="expert-preview-meta muted">
          协作拓扑：{expert.coordination.pattern}
        </p>
      )}
      {quickPrompts.length > 0 && (
        <div className="expert-preview-prompts">
          <h4>快捷提示</h4>
          <ul>
            {quickPrompts.map((prompt) => (
              <li key={prompt}>
                <button
                  type="button"
                  className="expert-preview-prompt-chip"
                  title="复制到剪贴板"
                  onClick={() => void navigator.clipboard.writeText(prompt)}
                >
                  {prompt}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
