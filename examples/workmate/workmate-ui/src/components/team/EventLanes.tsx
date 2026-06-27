import { useEffect, useRef } from 'react';
import type { BusLaneHighlight } from '../../lib/busLaneHighlight';
import { busLaneEntryKey } from '../../lib/busLaneHighlight';
import type { TeamUiLabels } from '../../lib/teamUiLabels';
import type { TeamBusPublishSource, TeamState } from '../../lib/teamStatus';

interface EventLanesProps {
  team: TeamState;
  labels: TeamUiLabels;
  busLaneHighlight?: BusLaneHighlight | null;
  onBusLaneSelect?: (highlight: BusLaneHighlight) => void;
}

const LANE_ORDER = ['ingress', 'bus', 'team', 'tool', 'approval', 'artifact', 'run', 'message', 'system'];

function laneLabel(topic: string, labels: TeamUiLabels): string {
  switch (topic) {
    case 'ingress':
      return labels.busLaneIngress;
    case 'bus':
      return labels.busLaneBus;
    default:
      return topic;
  }
}

function busPublishBadge(source?: TeamBusPublishSource): string | null {
  switch (source) {
    case 'orchestrator':
      return '编排';
    case 'mid-run':
      return '运行中';
    case 'outcome':
      return '结案';
    default:
      return null;
  }
}

/** message-bus 拓扑：按 topic 泳道展示 team.bus.* 与成员 topic。 */
export function EventLanes({
  team,
  labels,
  busLaneHighlight,
  onBusLaneSelect,
}: EventLanesProps) {
  const lanes = team.busLanes ?? {};
  const highlightedCardRef = useRef<HTMLLIElement | null>(null);
  const topics = Object.keys(lanes).sort((a, b) => {
    const ia = LANE_ORDER.indexOf(a);
    const ib = LANE_ORDER.indexOf(b);
    if (ia >= 0 && ib >= 0) {
      return ia - ib;
    }
    if (ia >= 0) {
      return -1;
    }
    if (ib >= 0) {
      return 1;
    }
    return a.localeCompare(b);
  });

  useEffect(() => {
    if (!busLaneHighlight) {
      return;
    }
    highlightedCardRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }, [busLaneHighlight]);

  if (topics.length === 0) {
    return (
      <section className="team-event-lanes muted" aria-label={labels.messageBusTitle}>
        <header className="team-event-lanes-head">{labels.messageBusTitle} · 等待事件…</header>
      </section>
    );
  }

  return (
    <section className="team-event-lanes" aria-label={labels.messageBusTitle}>
      <header className="team-event-lanes-head">
        {labels.messageBusTitle}
        {team.busMode === 'async-subscribe-multiwave' && (
          <span className="team-event-lanes-mode">多轮异步</span>
        )}
        {team.busMode === 'async-subscribe' && (
          <span className="team-event-lanes-mode">异步订阅</span>
        )}
        {team.topicBusProvider && (
          <span className="team-event-lanes-mode">{team.topicBusProvider}</span>
        )}
        {team.bus && team.bus.maxIterations > 1 && (
          <span className="team-event-lanes-wave">
            轮次 {team.bus.iteration > 0 ? `${team.bus.iteration}/${team.bus.maxIterations}` : `最多 ${team.bus.maxIterations}`}
          </span>
        )}
        · {team.busEntryCount ?? topics.reduce((n, t) => n + lanes[t].length, 0)} 条
      </header>
      {team.busSubscriptions && team.busSubscriptions.length > 0 && (
        <div className="team-event-lanes-subs muted">
          订阅：{team.busSubscriptions.map((s) => `${s.subscriberMemberId}→${s.topics.join(',')}`).join(' · ')}
        </div>
      )}
      <div className="team-event-lanes-grid">
        {topics.map((topic) => (
          <div key={topic} className="team-event-lane">
            <div className="team-event-lane-title">{laneLabel(topic, labels)}</div>
            <ul className="team-event-lane-list">
              {lanes[topic].map((entry, idx) => {
                const highlighted =
                  busLaneHighlight?.topic === topic && busLaneHighlight.index === idx;
                const laneKey = busLaneEntryKey(topic, idx);
                return (
                  <li
                    key={laneKey}
                    ref={highlighted ? highlightedCardRef : undefined}
                    className={`team-event-lane-card${highlighted ? ' team-event-lane-card-highlighted' : ''}`}
                    onClick={() => onBusLaneSelect?.({ topic, index: idx })}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onBusLaneSelect?.({ topic, index: idx });
                      }
                    }}
                    role={onBusLaneSelect ? 'button' : undefined}
                    tabIndex={onBusLaneSelect ? 0 : undefined}
                  >
                    <div className="team-event-lane-meta">
                      <span className="team-event-lane-author">{entry.authorName ?? entry.authorMemberId}</span>
                      {busPublishBadge(entry.publishSource) && (
                        <span className="team-event-lane-source">{busPublishBadge(entry.publishSource)}</span>
                      )}
                    </div>
                    {entry.preview && <p className="team-event-lane-preview">{entry.preview}</p>}
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}
