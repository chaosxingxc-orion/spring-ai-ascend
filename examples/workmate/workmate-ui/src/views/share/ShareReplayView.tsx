import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getShareReplay, buildShareArtifactDownloadUrl } from '../../api/share';
import {
  parseShareProjectionMode,
  projectShareChatItems,
  type ShareChatProjectionMode,
} from '../../lib/acp/shareChatProjection';
import { buildUserTurns, replayStatusLabel, sliceItemsForReplay } from '../../lib/shareReplay';
import { artifactDisplayName, findTeamBlackboardPath } from '../../lib/artifactDisplay';
import { NEW_TASK_PATH } from '../../lib/paths';
import { ChatMessageList } from '../session/ChatMessageList';
import { AiDisclaimer } from '../../components/AiDisclaimer';
import { formatDateTime } from '../../lib/formatLocale';
import type { ChatItem } from '../../types/events';

const REPLAY_STEP_MS = 700;

interface ShareReplayViewProps {
  token: string;
}

/** G15 + W38 — read-only task share + step replay with optional run-events/ACP projection. */
export function ShareReplayView({ token }: ShareReplayViewProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const projectionMode = parseShareProjectionMode(searchParams.get('projection'));
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState('任务回放');
  const [sharedAt, setSharedAt] = useState<string | null>(null);
  const [shareScope, setShareScope] = useState<string>('full');
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [artifacts, setArtifacts] = useState<Array<{ path: string; name: string; mime: string; size: number }>>([]);
  const [replayMode, setReplayMode] = useState(false);
  const [playing, setPlaying] = useState(false);
  const [visibleEndIndex, setVisibleEndIndex] = useState(-1);
  const [turnIndex, setTurnIndex] = useState(0);
  const timerRef = useRef<number | null>(null);

  const userTurns = useMemo(() => buildUserTurns(items), [items]);
  const knownWorkspacePaths = useMemo(() => new Set(artifacts.map((artifact) => artifact.path)), [artifacts]);
  const teamBlackboardPath = useMemo(() => findTeamBlackboardPath(artifacts.map((a) => a.path)), [artifacts]);
  const visibleItems = replayMode ? sliceItemsForReplay(items, visibleEndIndex) : items;
  const statusLabel = replayStatusLabel(playing, visibleEndIndex, items.length);

  const setProjectionMode = (mode: ShareChatProjectionMode) => {
    const next = new URLSearchParams(searchParams);
    if (mode === 'messages') {
      next.delete('projection');
    } else {
      next.set('projection', mode);
    }
    setSearchParams(next, { replace: true });
  };

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    void getShareReplay(token)
      .then((payload) => {
        if (cancelled) {
          return;
        }
        const projected = projectShareChatItems(payload.messages, payload.events, projectionMode);
        setTitle(payload.title || '任务回放');
        setSharedAt(payload.sharedAt);
        setShareScope(payload.scope ?? 'full');
        setExpiresAt(payload.expiresAt ?? null);
        setItems(projected);
        setArtifacts(payload.artifacts ?? []);
        setVisibleEndIndex(projected.length - 1);
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [projectionMode, token]);

  const clearTimer = () => {
    if (timerRef.current != null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };

  useEffect(() => () => clearTimer(), []);

  useEffect(() => {
    if (!playing || !replayMode) {
      clearTimer();
      return;
    }
    timerRef.current = window.setInterval(() => {
      setVisibleEndIndex((current) => {
        if (current >= items.length - 1) {
          setPlaying(false);
          return current;
        }
        return current + 1;
      });
    }, REPLAY_STEP_MS);
    return clearTimer;
  }, [items.length, playing, replayMode]);

  const startReplay = () => {
    setReplayMode(true);
    setVisibleEndIndex(-1);
    setTurnIndex(0);
    setPlaying(true);
  };

  const togglePlay = () => {
    if (!replayMode) {
      startReplay();
      return;
    }
    if (visibleEndIndex >= items.length - 1) {
      setVisibleEndIndex(-1);
      setTurnIndex(0);
      setPlaying(true);
      return;
    }
    setPlaying((value) => !value);
  };

  const skipForward = () => {
    if (!replayMode) {
      startReplay();
      return;
    }
    setVisibleEndIndex((current) => Math.min(items.length - 1, current + 3));
  };

  const goPrevUser = () => {
    if (!userTurns.length) {
      return;
    }
    const nextIndex = Math.max(0, turnIndex - 1);
    setTurnIndex(nextIndex);
    setReplayMode(true);
    setVisibleEndIndex(userTurns[nextIndex].endIndex);
    setPlaying(false);
  };

  const goNextUser = () => {
    if (!userTurns.length) {
      return;
    }
    const nextIndex = Math.min(userTurns.length - 1, turnIndex + 1);
    setTurnIndex(nextIndex);
    setReplayMode(true);
    setVisibleEndIndex(userTurns[nextIndex].endIndex);
    setPlaying(false);
  };

  const showAll = () => {
    setReplayMode(false);
    setPlaying(false);
    setVisibleEndIndex(items.length - 1);
  };

  if (loading) {
    return (
      <main className="share-replay-view">
        <p className="market-empty">正在加载分享内容…</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="share-replay-view">
        <header className="share-replay-header">
          <h1>分享不可用</h1>
          <Link to={NEW_TASK_PATH} className="share-replay-home-link">返回 WorkMate</Link>
        </header>
        <p className="memory-settings-error" role="alert">{error}</p>
      </main>
    );
  }

  return (
    <main className="share-replay-view">
      <header className="share-replay-header">
        <div>
          <p className="share-replay-eyebrow">只读分享 · 已脱敏</p>
          <h1>{title}</h1>
          {sharedAt && (
            <p className="share-replay-meta">
              分享于 {formatDateTime(sharedAt)}
              {expiresAt && ` · 有效期至 ${formatDateTime(expiresAt)}`}
            </p>
          )}
        </div>
        <Link to={NEW_TASK_PATH} className="share-replay-home-link">返回 WorkMate</Link>
      </header>

      <div className="share-replay-toolbar" role="toolbar" aria-label="回放控制">
        <button type="button" onClick={togglePlay}>
          {!replayMode || visibleEndIndex < 0 ? '开始回放' : playing ? '暂停' : '继续'}
        </button>
        <button type="button" onClick={skipForward} disabled={!items.length}>
          快进
        </button>
        <button type="button" onClick={goPrevUser} disabled={!userTurns.length}>
          上一条用户消息
        </button>
        <button type="button" onClick={goNextUser} disabled={!userTurns.length}>
          下一条用户消息
        </button>
        <button type="button" onClick={showAll} disabled={!items.length}>
          查看全部
        </button>
        <label className="share-replay-projection">
          投影
          <select
            value={projectionMode}
            aria-label="分享回放投影模式"
            onChange={(event) =>
              setProjectionMode(parseShareProjectionMode(event.target.value))
            }
          >
            <option value="messages">messages</option>
            <option value="run-events">run-events</option>
            <option value="acp-roundtrip">acp-roundtrip</option>
          </select>
        </label>
        <span className="share-replay-status" aria-live="polite">{statusLabel}</span>
      </div>

      <div className="share-replay-body">
        {shareScope !== 'artifacts' && (
        <section className="share-replay-chat" aria-label="对话回放">
          {items.length === 0 ? (
            <p className="market-empty">此任务暂无对话记录。</p>
          ) : (
            <ChatMessageList
              items={visibleItems}
              streaming={false}
              knownWorkspacePaths={knownWorkspacePaths}
              sessionArtifactCount={artifacts.length}
              teamBlackboardPath={teamBlackboardPath}
            />
          )}
        </section>
        )}

        {shareScope !== 'messages' && (
        <aside className="share-replay-artifacts" aria-label="任务产物">
          <h2>产物</h2>
          {artifacts.length === 0 ? (
            <p className="settings-field-hint">暂无文件产物。</p>
          ) : (
            <ul className="share-artifact-list">
              {artifacts.map((artifact) => (
                <li key={artifact.path}>
                  <span className="share-artifact-name">
                    {artifactDisplayName(artifact.path, artifact.name)}
                  </span>
                  <span className="share-artifact-meta">{artifact.mime} · {artifact.size} B</span>
                  <a
                    className="btn ghost compact"
                    href={buildShareArtifactDownloadUrl(token, artifact.path)}
                    download={artifact.name}
                  >
                    下载
                  </a>
                </li>
              ))}
            </ul>
          )}
        </aside>
        )}
      </div>

      <AiDisclaimer />
    </main>
  );
}
