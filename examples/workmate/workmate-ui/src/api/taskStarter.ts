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
  return response.json() as Promise<T>;
}

export interface LocalGitRepo {
  path: string;
  name: string;
  currentBranch: string;
}

export interface GitBranch {
  name: string;
  current: boolean;
}

export interface GithubRepo {
  owner: string;
  name: string;
  fullName: string;
  defaultBranch: string;
  cloneUrl: string;
}

export interface GithubStatus {
  configured: boolean;
  message: string;
}

export interface CloneGithubRepoResult {
  workspacePath: string;
  branch: string;
  cloned: boolean;
}

export function getGithubStatus() {
  return request<GithubStatus>('/api/v1/task-starter/github/status');
}

export function listLocalGitRepos() {
  return request<LocalGitRepo[]>('/api/v1/task-starter/git/local-repos');
}

export function listGitBranches(path: string) {
  return request<GitBranch[]>(`/api/v1/task-starter/git/branches?path=${encodeURIComponent(path)}`);
}

export function searchGithubRepos(query?: string) {
  const suffix = query?.trim() ? `?q=${encodeURIComponent(query.trim())}` : '';
  return request<GithubRepo[]>(`/api/v1/task-starter/github/repos${suffix}`);
}

export function listGithubBranches(owner: string, repo: string) {
  return request<GitBranch[]>(
    `/api/v1/task-starter/github/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/branches`,
  );
}

export function cloneGithubRepo(owner: string, repo: string, branch?: string) {
  return request<CloneGithubRepoResult>('/api/v1/task-starter/github/clone', {
    method: 'POST',
    body: JSON.stringify({ owner, repo, branch: branch ?? null }),
  });
}
