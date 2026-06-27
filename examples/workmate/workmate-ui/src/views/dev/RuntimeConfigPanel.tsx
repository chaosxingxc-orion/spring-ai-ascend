import { useCallback, useEffect, useState } from 'react';
import { getStudioRuntimeOverview } from '../../api/studio';
import type { StudioRuntimeOverview } from '../../types/studio';

export function RuntimeConfigPanel() {
  const [runtime, setRuntime] = useState<StudioRuntimeOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRuntime(await getStudioRuntimeOverview());
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <p className="muted">加载运行时配置…</p>;
  }

  if (error) {
    return <div className="dev-studio-error">{error}</div>;
  }

  if (!runtime) {
    return null;
  }

  return (
    <div className="dev-studio-runtime-panel">
      <div className="dev-studio-toolbar">
        <h2>运行时配置（只读）</h2>
        <button type="button" className="btn ghost sm" onClick={() => void load()}>
          刷新
        </button>
      </div>
      <p className="muted dev-studio-hint">模型目录来自 `workmate.llm.catalog`；MCP/连接器状态来自运行时注册表。</p>

      <section className="dev-studio-runtime-section">
        <h3>Model Catalog</h3>
        <p className="muted">默认模型：<code>{runtime.models.defaultModelId}</code></p>
        <div className="dev-studio-table-wrap">
          <table className="dev-studio-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>名称</th>
                <th>Provider</th>
                <th>Capabilities</th>
              </tr>
            </thead>
            <tbody>
              {runtime.models.models.map((model) => (
                <tr key={model.id}>
                  <td><code>{model.id}</code></td>
                  <td>{model.displayName}</td>
                  <td>{model.provider ?? '—'}</td>
                  <td>{(model.capabilities ?? []).join(', ') || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="dev-studio-runtime-section">
        <h3>MCP Servers</h3>
        <div className="dev-studio-table-wrap">
          <table className="dev-studio-table">
            <thead>
              <tr>
                <th>Server</th>
                <th>Connected</th>
                <th>Tools</th>
                <th>Invalid</th>
              </tr>
            </thead>
            <tbody>
              {runtime.mcpServers.map((server) => (
                <tr key={server.serverId}>
                  <td><code>{server.serverId}</code></td>
                  <td>{server.connected ? '是' : '否'}</td>
                  <td>{server.toolCount}</td>
                  <td>{server.invalidSchemaCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {runtime.mcpServers.length === 0 && <p className="muted dev-studio-empty">暂无 MCP 服务</p>}
        </div>
      </section>

      <section className="dev-studio-runtime-section">
        <h3>Connectors</h3>
        <div className="dev-studio-table-wrap">
          <table className="dev-studio-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>名称</th>
                <th>Status</th>
                <th>Tools</th>
                <th>Runnable</th>
              </tr>
            </thead>
            <tbody>
              {runtime.connectors.map((connector) => (
                <tr key={connector.id}>
                  <td><code>{connector.id}</code></td>
                  <td>{connector.name}</td>
                  <td>{connector.status}</td>
                  <td>{connector.toolCount}</td>
                  <td>{connector.runnable ? '是' : '否'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="dev-studio-runtime-section">
        <h3>MCP Tools ({runtime.mcpTools.length})</h3>
        <div className="dev-studio-table-wrap">
          <table className="dev-studio-table">
            <thead>
              <tr>
                <th>Tool ID</th>
                <th>Server</th>
                <th>描述</th>
              </tr>
            </thead>
            <tbody>
              {runtime.mcpTools.slice(0, 40).map((tool) => (
                <tr key={tool.openJiuwenToolId}>
                  <td><code>{tool.openJiuwenToolId}</code></td>
                  <td>{tool.serverId}</td>
                  <td>{tool.description ?? tool.toolName}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {runtime.mcpTools.length > 40 && (
            <p className="muted dev-studio-hint">仅显示前 40 个工具。</p>
          )}
        </div>
      </section>
    </div>
  );
}
