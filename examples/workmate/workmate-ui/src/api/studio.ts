import type {
  StudioDryRunResult,
  StudioExpertListItem,
  StudioExpertSource,
  StudioExpertCapabilities,
  StudioExpertWriteBody,
  StudioReloadResult,
  StudioSkillListItem,
  StudioSkillSource,
  StudioSkillFileEntry,
  StudioSkillFileContent,
  StudioSkillWriteBody,
  StudioTeamView,
  StudioTeamWriteBody,
  StudioRuntimePreview,
  StudioCoordinationWriteBody,
  StudioValidationResult,
  StudioAssetDiff,
  StudioExportPreview,
  StudioWelcomeSource,
  StudioWelcomeWriteBody,
  StudioPlaybookListItem,
  StudioPlaybookSource,
  StudioPlaybookWriteBody,
  StudioConfig,
  StudioDraftMeta,
  StudioRuntimeOverview,
} from '../types/studio';

const API_BASE = import.meta.env.VITE_API_BASE ?? '';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    throw new Error('API 返回了非 JSON 响应');
  }
  return response.json() as Promise<T>;
}

export function studioReload() {
  return request<StudioReloadResult>('/api/v1/studio/reload', { method: 'POST' });
}

export function listStudioExperts() {
  return request<StudioExpertListItem[]>('/api/v1/studio/experts');
}

export function listStudioSkills() {
  return request<StudioSkillListItem[]>('/api/v1/studio/skills');
}

export function getStudioExpertSource(expertId: string) {
  return request<StudioExpertSource>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/source`);
}

export function getStudioExpertCapabilities(expertId: string) {
  return request<StudioExpertCapabilities>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/capabilities`);
}

export function createStudioExpert(body: StudioExpertWriteBody) {
  return request<StudioExpertSource>('/api/v1/studio/experts', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateStudioExpert(expertId: string, body: StudioExpertWriteBody) {
  return request<StudioExpertSource>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function deleteStudioExpert(expertId: string) {
  return request<void>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}`, {
    method: 'DELETE',
  });
}

export function forkStudioExpert(expertId: string) {
  return request<StudioExpertSource>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/fork`, {
    method: 'POST',
  });
}

export function validateStudioExpert(body: StudioExpertWriteBody) {
  return request<StudioValidationResult>('/api/v1/studio/experts/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function dryRunStudioExpert(expertId: string) {
  return request<StudioDryRunResult>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/dry-run`, {
    method: 'POST',
  });
}

export async function importStudioExpertZip(file: File) {
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`${API_BASE}/api/v1/studio/experts/import/zip`, {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  return response.json() as Promise<StudioExpertSource>;
}

export function getStudioSkillSource(skillId: string) {
  return request<StudioSkillSource>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}/source`);
}

export function listStudioSkillFiles(skillId: string) {
  return request<StudioSkillFileEntry[]>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}/files`);
}

export function getStudioSkillFileContent(skillId: string, path: string) {
  const params = new URLSearchParams({ path });
  return request<StudioSkillFileContent>(
    `/api/v1/studio/skills/${encodeURIComponent(skillId)}/files/content?${params.toString()}`,
  );
}

export function createStudioSkill(body: StudioSkillWriteBody) {
  return request<StudioSkillSource>('/api/v1/studio/skills', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateStudioSkill(skillId: string, body: StudioSkillWriteBody) {
  return request<StudioSkillSource>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function deleteStudioSkill(skillId: string) {
  return request<void>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}`, {
    method: 'DELETE',
  });
}

export function validateStudioSkill(body: StudioSkillWriteBody) {
  return request<StudioValidationResult>('/api/v1/studio/skills/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getStudioTeam(teamId: string) {
  return request<StudioTeamView>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}`);
}

export function createStudioTeam(body: StudioTeamWriteBody) {
  return request<StudioTeamView>('/api/v1/studio/teams', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateStudioTeam(teamId: string, body: StudioTeamWriteBody) {
  return request<StudioTeamView>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function validateStudioTeam(body: StudioTeamWriteBody) {
  return request<StudioValidationResult>('/api/v1/studio/teams/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function previewStudioTeamRuntime(teamId: string, body: StudioTeamWriteBody) {
  return request<StudioRuntimePreview>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}/runtime-preview`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateStudioTeamCoordination(teamId: string, coordination: StudioCoordinationWriteBody) {
  return request<StudioTeamView>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}/coordination`, {
    method: 'PUT',
    body: JSON.stringify(coordination),
  });
}

export function dryRunStudioTeam(teamId: string) {
  return dryRunStudioExpert(teamId);
}

export function getStudioExpertDiff(expertId: string) {
  return request<StudioAssetDiff>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/diff`);
}

export function rollbackStudioExpert(expertId: string) {
  return request<void>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/rollback`, { method: 'POST' });
}

export function getStudioSkillDiff(skillId: string) {
  return request<StudioAssetDiff>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}/diff`);
}

export function rollbackStudioSkill(skillId: string) {
  return request<void>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}/rollback`, { method: 'POST' });
}

export function getStudioTeamDiff(teamId: string) {
  return request<StudioAssetDiff>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}/diff`);
}

export function rollbackStudioTeam(teamId: string) {
  return request<void>(`/api/v1/studio/teams/${encodeURIComponent(teamId)}/rollback`, { method: 'POST' });
}

export function getStudioExportPreview() {
  return request<StudioExportPreview>('/api/v1/studio/export/preview');
}

async function downloadStudioZip(path: string, filename: string) {
  const response = await fetch(`${API_BASE}${path}`);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `HTTP ${response.status}`);
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

export function downloadStudioExpertExport(expertId: string) {
  return downloadStudioZip(
    `/api/v1/studio/experts/${encodeURIComponent(expertId)}/export`,
    `${expertId}-office-export.zip`,
  );
}

export function downloadStudioSkillExport(skillId: string) {
  return downloadStudioZip(
    `/api/v1/studio/skills/${encodeURIComponent(skillId)}/export`,
    `${skillId}-office-export.zip`,
  );
}

export function downloadStudioAllExports() {
  return downloadStudioZip('/api/v1/studio/export/all', 'workmate-office-drafts.zip');
}

export function getStudioWelcomeSource() {
  return request<StudioWelcomeSource>('/api/v1/studio/welcome/source');
}

export function updateStudioWelcome(body: StudioWelcomeWriteBody) {
  return request<StudioWelcomeSource>('/api/v1/studio/welcome', {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function validateStudioWelcome(body: StudioWelcomeWriteBody) {
  return request<StudioValidationResult>('/api/v1/studio/welcome/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getStudioWelcomeDiff() {
  return request<StudioAssetDiff>('/api/v1/studio/welcome/diff');
}

export function rollbackStudioWelcome() {
  return request<void>('/api/v1/studio/welcome/rollback', { method: 'POST' });
}

export function downloadStudioWelcomeExport() {
  return downloadStudioZip('/api/v1/studio/welcome/export', 'welcome-office-export.zip');
}

export function listStudioPlaybooks() {
  return request<StudioPlaybookListItem[]>('/api/v1/studio/playbooks');
}

export function getStudioPlaybookSource(playbookId: string) {
  return request<StudioPlaybookSource>(`/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}/source`);
}

export function createStudioPlaybook(body: StudioPlaybookWriteBody) {
  return request<StudioPlaybookSource>('/api/v1/studio/playbooks', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateStudioPlaybook(playbookId: string, body: StudioPlaybookWriteBody) {
  return request<StudioPlaybookSource>(`/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function validateStudioPlaybook(body: StudioPlaybookWriteBody) {
  return request<StudioValidationResult>('/api/v1/studio/playbooks/validate', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function getStudioPlaybookDiff(playbookId: string) {
  return request<StudioAssetDiff>(`/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}/diff`);
}

export function rollbackStudioPlaybook(playbookId: string) {
  return request<void>(`/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}/rollback`, { method: 'POST' });
}

export function downloadStudioPlaybookExport(playbookId: string) {
  return downloadStudioZip(
    `/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}/export`,
    `${playbookId}-office-export.zip`,
  );
}

export function getStudioConfig() {
  return request<StudioConfig>('/api/v1/studio/config');
}

export function getStudioDraftMeta(assetType: string, assetId: string) {
  return request<StudioDraftMeta>(
    `/api/v1/studio/draft-meta/${encodeURIComponent(assetType)}/${encodeURIComponent(assetId)}`,
  );
}

export function getStudioRuntimeOverview() {
  return request<StudioRuntimeOverview>('/api/v1/studio/runtime');
}

export function publishStudioExpert(expertId: string) {
  return request<StudioDraftMeta>(`/api/v1/studio/experts/${encodeURIComponent(expertId)}/publish`, { method: 'POST' });
}

export function publishStudioSkill(skillId: string) {
  return request<StudioDraftMeta>(`/api/v1/studio/skills/${encodeURIComponent(skillId)}/publish`, { method: 'POST' });
}

export function publishStudioPlaybook(playbookId: string) {
  return request<StudioDraftMeta>(`/api/v1/studio/playbooks/${encodeURIComponent(playbookId)}/publish`, { method: 'POST' });
}

export function publishStudioWelcome() {
  return request<StudioDraftMeta>('/api/v1/studio/welcome/publish', { method: 'POST' });
}
