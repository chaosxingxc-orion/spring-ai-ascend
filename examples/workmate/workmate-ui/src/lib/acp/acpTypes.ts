export type { AcpSessionUpdate } from './runEventToAcp';

export interface RunEventDraft {
  name: string;
  data: Record<string, unknown>;
}
