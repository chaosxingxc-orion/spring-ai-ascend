import { ApprovalModal } from '../../components/ApprovalModal';
import { MaxSessionsDialog } from '../../components/MaxSessionsDialog';
import { ChangeExpertDialog } from '../../views/session/ChangeExpertDialog';
import { CloudSessionDrawer } from '../../views/session/CloudSessionDrawer';
import { ShareDialog } from '../../views/share/ShareDialog';
import { SummonConfirmModal } from '../../views/market/SummonConfirmModal';
import { resolveTeamUiLabels } from '../../lib/teamUiLabels';
import { workspaceLabelForSession } from '../../lib/sessionWorkspace';
import type { Expert, Session, SessionLimits, ApprovalDecision, ApprovalDecisionScope } from '../../types/api';
import type { WorkspacePreset } from '../../types/workspace';
import type { ApprovalRequiredPayload } from '../../types/events';
import type { CloudSession } from '../../api/cloud';
import type { ShareLink } from '../../api/share';

export interface AppShellOverlaysProps {
  modalPending: ApprovalRequiredPayload | null;
  approvalBusy: boolean;
  activeExpert: Expert | null;
  activeId: string | null;
  onModalDecide: (decision: ApprovalDecision, scope?: ApprovalDecisionScope) => void;
  onModalClose: () => void;
  shareDialogOpen: boolean;
  activeSessionTitle: string;
  onShareDialogClose: () => void;
  onShared: (link: ShareLink, url: string) => void;
  changeExpertOpen: boolean;
  experts: Expert[];
  activeSession: Session | null;
  workspacePresets: WorkspacePreset[];
  changeExpertBusy: boolean;
  creatingSession: boolean;
  onChangeExpertClose: () => void;
  onChangeExpertConfirm: (expertId: string, archiveCurrent: boolean) => void;
  cloudDrawerOpen: boolean;
  linkedCloudSession: CloudSession | null;
  onCloudDrawerClose: () => void;
  onOpenAutomation: () => void;
  maxSessionsDialog: SessionLimits | null;
  autoArchiveEnabled: boolean;
  sessions: Session[];
  onArchiveFromLimitDialog: (sessionId: string) => Promise<void>;
  onAutoArchiveBulk: (count: number) => Promise<void>;
  onMaxSessionsDismiss: () => void;
  summonExpert: Expert | null;
  summonBusy: boolean;
  summonContinueSession: boolean;
  onSummonConfirm: () => void;
  onSummonCancel: () => void;
}

export function AppShellOverlays({
  modalPending,
  approvalBusy,
  activeExpert,
  activeId,
  onModalDecide,
  onModalClose,
  shareDialogOpen,
  activeSessionTitle,
  onShareDialogClose,
  onShared,
  changeExpertOpen,
  experts,
  activeSession,
  workspacePresets,
  changeExpertBusy,
  creatingSession,
  onChangeExpertClose,
  onChangeExpertConfirm,
  cloudDrawerOpen,
  linkedCloudSession,
  onCloudDrawerClose,
  onOpenAutomation,
  maxSessionsDialog,
  autoArchiveEnabled,
  sessions,
  onArchiveFromLimitDialog,
  onAutoArchiveBulk,
  onMaxSessionsDismiss,
  summonExpert,
  summonBusy,
  summonContinueSession,
  onSummonConfirm,
  onSummonCancel,
}: AppShellOverlaysProps) {
  return (
    <>
      <ApprovalModal
        pending={modalPending}
        busy={approvalBusy}
        labels={resolveTeamUiLabels(activeExpert)}
        onDecide={(decision, scope) => void onModalDecide(decision, scope)}
        onClose={onModalClose}
      />
      <ShareDialog
        open={shareDialogOpen && Boolean(activeId)}
        sessionId={activeId ?? ''}
        sessionTitle={activeSessionTitle}
        busy={false}
        onClose={onShareDialogClose}
        onShared={onShared}
      />
      <ChangeExpertDialog
        open={changeExpertOpen}
        experts={experts}
        currentExpertId={activeSession?.expertId}
        sessionTitle={activeSessionTitle}
        workspaceLabel={
          activeSession ? workspaceLabelForSession(activeSession, workspacePresets) : undefined
        }
        busy={changeExpertBusy || creatingSession}
        onClose={onChangeExpertClose}
        onConfirm={(expertId, archiveCurrent) => void onChangeExpertConfirm(expertId, archiveCurrent)}
      />
      <CloudSessionDrawer
        session={cloudDrawerOpen ? linkedCloudSession : null}
        onClose={onCloudDrawerClose}
        onOpenAutomation={() => onOpenAutomation()}
      />
      {maxSessionsDialog && (
        <MaxSessionsDialog
          activeCount={maxSessionsDialog.activeCount}
          maxActive={maxSessionsDialog.maxActive}
          archivableCount={maxSessionsDialog.archivableCount ?? 0}
          autoArchiveEnabled={autoArchiveEnabled}
          sessions={sessions}
          onArchiveSession={(sessionId) => onArchiveFromLimitDialog(sessionId)}
          onAutoArchiveBulk={(count) => onAutoArchiveBulk(count)}
          onDismiss={onMaxSessionsDismiss}
        />
      )}
      <SummonConfirmModal
        expert={summonExpert}
        busy={summonBusy}
        continueSession={summonContinueSession}
        onConfirm={() => void onSummonConfirm()}
        onCancel={onSummonCancel}
      />
    </>
  );
}
