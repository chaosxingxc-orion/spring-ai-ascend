import type { StudioTeamMemberWriteBody } from '../../types/studio';

interface TeamOrchestrationCanvasProps {
  pattern: string;
  teamName: string;
  leadName?: string;
  members: StudioTeamMemberWriteBody[];
  hasLead: boolean;
  selectedMemberId?: string | null;
  onSelectMember?: (memberId: string) => void;
}

function MemberNode({
  member,
  selected,
  onSelect,
}: {
  member: StudioTeamMemberWriteBody;
  selected: boolean;
  onSelect?: () => void;
}) {
  return (
    <button
      type="button"
      className={`dev-studio-canvas-node dev-studio-canvas-member${selected ? ' selected' : ''}`}
      onClick={onSelect}
      aria-pressed={selected}
    >
      <span className="dev-studio-canvas-node-avatar">{member.avatar ?? '🧑'}</span>
      <span className="dev-studio-canvas-node-label">{member.name}</span>
      <span className="dev-studio-canvas-node-sub">{member.role ?? member.id}</span>
    </button>
  );
}

function LeadNode({ name }: { name: string }) {
  return (
    <div className="dev-studio-canvas-node dev-studio-canvas-lead">
      <span className="dev-studio-canvas-node-avatar">👑</span>
      <span className="dev-studio-canvas-node-label">{name}</span>
      <span className="dev-studio-canvas-node-sub">主理人</span>
    </div>
  );
}

export function TeamOrchestrationCanvas({
  pattern,
  teamName,
  leadName,
  members,
  hasLead,
  selectedMemberId,
  onSelectMember,
}: TeamOrchestrationCanvasProps) {
  const sorted = [...members].sort((a, b) => (a.order ?? 0) - (b.order ?? 0));
  const lead = leadName || teamName;

  const renderMembers = (layout: 'star' | 'chain' | 'grid' | 'dual' | 'bus') => {
    if (layout === 'star' && hasLead) {
      return (
        <div className="dev-studio-canvas-star">
          <LeadNode name={lead} />
          <div className="dev-studio-canvas-star-members">
            {sorted.map((member) => (
              <MemberNode
                key={member.id}
                member={member}
                selected={selectedMemberId === member.id}
                onSelect={onSelectMember ? () => onSelectMember(member.id) : undefined}
              />
            ))}
          </div>
        </div>
      );
    }
    if (layout === 'chain') {
      return (
        <div className="dev-studio-canvas-chain">
          {sorted.map((member, index) => (
            <div key={member.id} className="dev-studio-canvas-chain-item">
              {index > 0 && <span className="dev-studio-canvas-arrow" aria-hidden>→</span>}
              <MemberNode
                member={member}
                selected={selectedMemberId === member.id}
                onSelect={onSelectMember ? () => onSelectMember(member.id) : undefined}
              />
            </div>
          ))}
        </div>
      );
    }
    if (layout === 'dual' && sorted.length >= 2) {
      return (
        <div className="dev-studio-canvas-dual">
          <MemberNode
            member={sorted[0]}
            selected={selectedMemberId === sorted[0].id}
            onSelect={onSelectMember ? () => onSelectMember(sorted[0].id) : undefined}
          />
          <span className="dev-studio-canvas-loop" aria-hidden>↔</span>
          <MemberNode
            member={sorted[1]}
            selected={selectedMemberId === sorted[1].id}
            onSelect={onSelectMember ? () => onSelectMember(sorted[1].id) : undefined}
          />
        </div>
      );
    }
    if (layout === 'bus') {
      return (
        <div className="dev-studio-canvas-bus">
          <div className="dev-studio-canvas-bus-line" aria-hidden />
          <div className="dev-studio-canvas-bus-members">
            {sorted.map((member) => (
              <MemberNode
                key={member.id}
                member={member}
                selected={selectedMemberId === member.id}
                onSelect={onSelectMember ? () => onSelectMember(member.id) : undefined}
              />
            ))}
          </div>
        </div>
      );
    }
    return (
      <div className="dev-studio-canvas-grid">
        {sorted.map((member) => (
          <MemberNode
            key={member.id}
            member={member}
            selected={selectedMemberId === member.id}
            onSelect={onSelectMember ? () => onSelectMember(member.id) : undefined}
          />
        ))}
      </div>
    );
  };

  let layout: 'star' | 'chain' | 'grid' | 'dual' | 'bus' = 'grid';
  switch (pattern) {
    case 'orchestrator':
      layout = 'star';
      break;
    case 'pipeline':
      layout = 'chain';
      break;
    case 'generator-verifier':
      layout = 'dual';
      break;
    case 'message-bus':
    case 'shared-state':
      layout = 'bus';
      break;
    case 'agent-team':
      layout = hasLead ? 'star' : 'grid';
      break;
    default:
      layout = 'grid';
  }

  return (
    <section className="dev-studio-canvas" aria-label="编排拓扑预览">
      <header className="dev-studio-canvas-header">
        <h2>{teamName}</h2>
        <span className="dev-studio-badge">{pattern}</span>
      </header>
      {renderMembers(layout)}
    </section>
  );
}
