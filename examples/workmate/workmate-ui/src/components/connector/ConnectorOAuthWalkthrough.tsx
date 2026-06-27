import type { OAuthWalkthroughStep, OAuthWalkthroughStepId } from '../../lib/connectorOAuthWalkthrough';

interface ConnectorOAuthWalkthroughProps {
  steps: OAuthWalkthroughStep[];
  activeStep: OAuthWalkthroughStepId;
}

export function ConnectorOAuthWalkthrough({ steps, activeStep }: ConnectorOAuthWalkthroughProps) {
  const activeIndex = steps.findIndex((step) => step.id === activeStep);

  return (
    <ol className="connector-oauth-walkthrough" aria-label="OAuth 连接引导">
      {steps.map((step, index) => {
        const state =
          index < activeIndex ? 'done' : index === activeIndex ? 'current' : 'pending';
        return (
          <li key={step.id} className={`connector-oauth-step connector-oauth-step-${state}`}>
            <span className="connector-oauth-step-index" aria-hidden>
              {index + 1}
            </span>
            <div>
              <strong>{step.title}</strong>
              <p className="market-hint">{step.detail}</p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
