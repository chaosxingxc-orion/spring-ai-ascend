import { useCallback, useEffect, useState } from 'react';
import {
  cloneGithubRepo,
  getGithubStatus,
  listGitBranches,
  listGithubBranches,
  listLocalGitRepos,
  searchGithubRepos,
  type GithubRepo,
  type LocalGitRepo,
} from '../api/taskStarter';

export interface GitSelection {
  workspacePath: string;
  branch: string;
  label: string;
}

interface GitTaskStarterPanelProps {
  onSelect: (selection: GitSelection) => void;
}

type Tab = 'local' | 'github';

export function GitTaskStarterPanel({ onSelect }: GitTaskStarterPanelProps) {
  const [tab, setTab] = useState<Tab>('local');
  const [localRepos, setLocalRepos] = useState<LocalGitRepo[]>([]);
  const [githubRepos, setGithubRepos] = useState<GithubRepo[]>([]);
  const [githubConfigured, setGithubConfigured] = useState(false);
  const [githubMessage, setGithubMessage] = useState('');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [branches, setBranches] = useState<string[]>([]);
  const [branchTarget, setBranchTarget] = useState<{ kind: 'local'; path: string } | { kind: 'github'; repo: GithubRepo } | null>(null);
  const [selectedBranch, setSelectedBranch] = useState('');

  const loadLocal = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setLocalRepos(await listLocalGitRepos());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadGithub = useCallback(async (search?: string) => {
    setLoading(true);
    setError(null);
    try {
      const [status, repos] = await Promise.all([getGithubStatus(), searchGithubRepos(search)]);
      setGithubConfigured(status.configured);
      setGithubMessage(status.message);
      setGithubRepos(repos);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === 'local') {
      void loadLocal();
    } else {
      void loadGithub();
    }
  }, [loadGithub, loadLocal, tab]);

  const openBranchPicker = async (target: { kind: 'local'; path: string; name: string } | { kind: 'github'; repo: GithubRepo }) => {
    setBusy(target.kind === 'local' ? target.path : target.repo.fullName);
    setError(null);
    try {
      const items =
        target.kind === 'local'
          ? await listGitBranches(target.path)
          : await listGithubBranches(target.repo.owner, target.repo.name);
      const names = items.map((item) => item.name);
      setBranches(names);
      const defaultBranch =
        target.kind === 'github' ? target.repo.defaultBranch : names[0] ?? '';
      setSelectedBranch(items.find((item) => item.current)?.name ?? defaultBranch);
      setBranchTarget(target.kind === 'local' ? { kind: 'local', path: target.path } : { kind: 'github', repo: target.repo });
    } catch (err) {
      setError((err as Error).message);
      setBranchTarget(null);
    } finally {
      setBusy(null);
    }
  };

  const confirmSelection = async () => {
    if (!branchTarget || !selectedBranch) {
      return;
    }
    setBusy('confirm');
    setError(null);
    try {
      if (branchTarget.kind === 'local') {
        const repo = localRepos.find((item) => item.path === branchTarget.path);
        onSelect({
          workspacePath: branchTarget.path,
          branch: selectedBranch,
          label: `${repo?.name ?? branchTarget.path} @ ${selectedBranch}`,
        });
      } else {
        const cloned = await cloneGithubRepo(branchTarget.repo.owner, branchTarget.repo.name, selectedBranch);
        onSelect({
          workspacePath: cloned.workspacePath,
          branch: cloned.branch,
          label: `${branchTarget.repo.fullName} @ ${cloned.branch}`,
        });
      }
      setBranchTarget(null);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="git-task-starter-panel">
      <div className="git-task-starter-tabs" role="tablist">
        <button type="button" role="tab" className={tab === 'local' ? 'active' : ''} onClick={() => setTab('local')}>
          本地 Git
        </button>
        <button type="button" role="tab" className={tab === 'github' ? 'active' : ''} onClick={() => setTab('github')}>
          GitHub
        </button>
      </div>

      {tab === 'github' && (
        <div className="git-task-starter-search">
          <input
            type="search"
            placeholder="搜索仓库…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                void loadGithub(query);
              }
            }}
          />
          <button type="button" className="btn ghost compact" onClick={() => void loadGithub(query)}>
            搜索
          </button>
        </div>
      )}

      {!githubConfigured && tab === 'github' && (
        <p className="settings-field-hint">{githubMessage || '请配置 GitHub Token（连接器 github 或 WORKMATE_GITHUB_TOKEN）'}</p>
      )}

      {error && <p className="memory-settings-error" role="alert">{error}</p>}
      {loading && <p className="settings-field-hint">加载中…</p>}

      {!branchTarget && tab === 'local' && (
        <ul className="git-task-starter-list">
          {localRepos.length === 0 && !loading && <li className="settings-field-hint">未发现本地 Git 仓库</li>}
          {localRepos.map((repo) => (
            <li key={repo.path}>
              <button type="button" disabled={busy === repo.path} onClick={() => void openBranchPicker({ kind: 'local', path: repo.path, name: repo.name })}>
                <strong>{repo.name}</strong>
                <span>{repo.path}{repo.currentBranch ? ` · ${repo.currentBranch}` : ''}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {!branchTarget && tab === 'github' && (
        <ul className="git-task-starter-list">
          {githubRepos.length === 0 && !loading && githubConfigured && <li className="settings-field-hint">无匹配仓库</li>}
          {githubRepos.map((repo) => (
            <li key={repo.fullName}>
              <button type="button" disabled={busy === repo.fullName} onClick={() => void openBranchPicker({ kind: 'github', repo })}>
                <strong>{repo.fullName}</strong>
                <span>默认分支 {repo.defaultBranch}</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {branchTarget && (
        <div className="git-task-starter-branch-picker">
          <label className="settings-field">
            <span className="settings-field-label">选择分支</span>
            <select value={selectedBranch} onChange={(event) => setSelectedBranch(event.target.value)}>
              {branches.map((branch) => (
                <option key={branch} value={branch}>{branch}</option>
              ))}
            </select>
          </label>
          <div className="git-task-starter-branch-actions">
            <button type="button" className="btn ghost compact" onClick={() => setBranchTarget(null)}>返回</button>
            <button type="button" className="btn primary compact" disabled={!selectedBranch || busy === 'confirm'} onClick={() => void confirmSelection()}>
              {busy === 'confirm' ? '准备中…' : '使用此仓库'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
