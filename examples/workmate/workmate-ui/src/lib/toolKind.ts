import { formatToolName } from './toolDisplay';
import type { TeamUiLabels } from './teamUiLabels';

export type ToolKind =
  | 'bash'
  | 'write'
  | 'read'
  | 'mcp'
  | 'team-bus-publish'
  | 'team-build'
  | 'team-send-message'
  | 'team-receive-message'
  | 'list'
  | 'delete'
  | 'web-search'
  | 'web-fetch'
  | 'skill'
  | 'generic';

function isWebSearchToolName(base: string): boolean {
  const normalized = base.toLowerCase();
  return (
    normalized.includes('websearch')
    || normalized.includes('web_search')
    || normalized.includes('web-search')
    || (normalized.includes('search') && normalized.includes('web'))
    || /^search$/i.test(base)
  );
}

function isWebFetchToolName(base: string): boolean {
  const normalized = base.toLowerCase();
  return (
    normalized.includes('webfetch')
    || normalized.includes('web_fetch')
    || normalized.includes('web-fetch')
    || (normalized.includes('fetch') && normalized.includes('web'))
  );
}

function isSkillToolName(base: string): boolean {
  const normalized = base.toLowerCase();
  return (
    normalized.includes('invoke_skill')
    || normalized.includes('run_skill')
    || normalized.includes('load_skill')
    || normalized.includes('use_skill')
    || /^skill(__|$)/.test(normalized)
    || (normalized.includes('skill') && !normalized.includes('skillcompat'))
  );
}

export function isTeamBusPublishTool(toolName: string): boolean {
  return formatToolName(toolName) === 'workmate_team_bus_publish';
}

function bashListCommand(command: string): boolean {
  const normalized = command.trim().toLowerCase();
  return /^(ls|find|tree|glob)\b/.test(normalized);
}

function bashDeleteCommand(command: string): boolean {
  const normalized = command.trim().toLowerCase();
  return /^(rm|rmdir|unlink|del)\b/.test(normalized);
}

export interface ClassifyToolOptions {
  /** When set, member-scoped {@code send_message} renders as a reply card (not delegation). */
  memberId?: string;
}

export function classifyTool(
  toolName: string,
  args?: unknown,
  options?: ClassifyToolOptions,
): ToolKind {
  if (isTeamBusPublishTool(toolName)) {
    return 'team-bus-publish';
  }
  const base = formatToolName(toolName).toLowerCase();
  if (base.includes('build_team') || base === 'team.build_team') {
    return 'team-build';
  }
  if (base.includes('send_message') && (base.includes('team') || base.startsWith('team.'))) {
    return 'team-send-message';
  }
  if (options?.memberId && base.includes('send_message')) {
    return 'team-receive-message';
  }
  if (base.includes('bash')) {
    const command = bashCommandFromArgs(args);
    if (command) {
      if (bashDeleteCommand(command)) {
        return 'delete';
      }
      if (bashListCommand(command)) {
        return 'list';
      }
    }
    return 'bash';
  }
  if (base.includes('glob') || base.includes('list') || base.includes('ls')) {
    return 'list';
  }
  if (base.includes('delete') || base.includes('remove') || base.includes('unlink')) {
    return 'delete';
  }
  if (isSkillToolName(base)) {
    return 'skill';
  }
  if (base.includes('write')) {
    return 'write';
  }
  if (base.includes('read')) {
    return 'read';
  }
  if (isWebSearchToolName(base)) {
    return 'web-search';
  }
  if (isWebFetchToolName(base)) {
    return 'web-fetch';
  }
  if (base.includes('mcp') || base.includes('connector')) {
    const { tool } = parseMcpToolName(toolName);
    const mcpTool = (tool ?? base).toLowerCase();
    if (isWebSearchToolName(mcpTool)) {
      return 'web-search';
    }
    if (isWebFetchToolName(mcpTool)) {
      return 'web-fetch';
    }
    return 'mcp';
  }
  return 'generic';
}

function pathFromArgs(args: unknown): string | undefined {
  return readArgField(args, 'path', 'file', 'url', 'command');
}

/** Persisted run-event args are redacted to `{ preview, bytes }`; live SSE args are plain strings. */
function readRedactableValue(value: unknown): string | undefined {
  if (typeof value === 'string' && value.trim()) {
    return value.trim();
  }
  if (value && typeof value === 'object') {
    const preview = (value as Record<string, unknown>).preview;
    if (typeof preview === 'string' && preview.trim()) {
      return preview.trim();
    }
  }
  return undefined;
}

/** Best-effort parse of a JSON object string; returns undefined when it is not a JSON object. */
function parseJsonObject(text: string): Record<string, unknown> | undefined {
  const trimmed = text.trim();
  if (!trimmed.startsWith('{')) {
    return undefined;
  }
  try {
    const parsed = JSON.parse(trimmed);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : undefined;
  } catch {
    return undefined;
  }
}

/**
 * Recover a string field from a JSON object string that the audit ledger may have truncated
 * mid-value (so it is no longer valid JSON, e.g. `{"command": "cat << EOF…`). Tolerant regex over
 * the raw text, then unescape.
 */
function fieldFromTruncatedJson(text: string, keys: string[]): string | undefined {
  for (const key of keys) {
    const match = text.match(new RegExp(`"${key}"\\s*:\\s*"((?:[^"\\\\]|\\\\.)*)`));
    if (match) {
      try {
        return JSON.parse(`"${match[1]}"`);
      } catch {
        return match[1];
      }
    }
  }
  return undefined;
}

/**
 * Read a string field out of tool args regardless of how they were captured: live args are a
 * structured record (or a JSON string), while persisted/audited args are redacted to
 * `{ preview, bytes, sha256 }` where `preview` is the whole args object stringified and truncated.
 * Without this, cards that read `args.command` / `args.path` on persisted data show nothing.
 */
function readArgField(args: unknown, ...keys: string[]): string | undefined {
  const fromRecord = (record: Record<string, unknown>): string | undefined => {
    for (const key of keys) {
      const direct = readRedactableValue(record[key]);
      if (direct) {
        return direct;
      }
    }
    return undefined;
  };
  if (typeof args === 'string') {
    const parsed = parseJsonObject(args);
    const value = parsed ? fromRecord(parsed) : undefined;
    return value ?? fieldFromTruncatedJson(args, keys);
  }
  if (args && typeof args === 'object') {
    const record = args as Record<string, unknown>;
    const direct = fromRecord(record);
    if (direct) {
      return direct;
    }
    // Whole-args redacted blob: { preview: "{\"command\":\"…", bytes, sha256 }.
    if (typeof record.preview === 'string' && !(keys.some((key) => key in record))) {
      const parsed = parseJsonObject(record.preview);
      const value = parsed ? fromRecord(parsed) : undefined;
      return value ?? fieldFromTruncatedJson(record.preview, keys);
    }
  }
  return undefined;
}

function delegationRecipientFromArgs(args: unknown): string | undefined {
  if (!args || typeof args !== 'object') {
    return undefined;
  }
  const record = args as Record<string, unknown>;
  return (
    readRedactableValue(record.memberName)
    ?? readRedactableValue(record.member_name)
    ?? readRedactableValue(record.to)
    ?? readRedactableValue(record.memberId)
    ?? readRedactableValue(record.member_id)
    ?? readRedactableValue(record.target)
  );
}

/** 深度思考步骤与运行命令标题 */
export function toolStepLabel(
  toolName: string,
  args?: unknown,
  options?: {
    labels?: TeamUiLabels;
    memberLabelForTopic?: (topic: string) => string | undefined;
  },
): string {
  const kind = classifyTool(toolName, args);
  if (kind === 'team-bus-publish') {
    const { topic } = busPublishPreviewFromArgs(args);
    if (topic && options?.memberLabelForTopic) {
      const member = options.memberLabelForTopic(topic);
      if (member && options.labels) {
        return `${member} ${options.labels.busPublishAction}`;
      }
    }
    if (options?.labels) {
      return options.labels.busPublishAction;
    }
    return topic ? `发布到总线 · ${topic}` : '发布到消息总线';
  }
  const hint =
    kind === 'web-search'
      ? webSearchQueryFromArgs(args)
      : kind === 'web-fetch'
        ? webFetchUrlFromArgs(args)
        : pathFromArgs(args);
  switch (kind) {
    case 'bash':
      return '运行命令';
    case 'list':
      return '列出文件';
    case 'delete':
      return '删除文件';
    case 'write':
      return hint ? `写入 ${hint}` : '写入文件';
    case 'read':
      return hint ? `读取 ${hint}` : '读取文件';
    case 'web-search':
      return hint ? `搜索 ${hint}` : '网页搜索';
    case 'web-fetch':
      return hint ? `获取 ${hint}` : '网页获取';
    case 'skill': {
      const skill = parseSkillPreview(args, toolName);
      return skill.detail ? `技能 · ${skill.name}` : `技能 · ${skill.name}`;
    }
    case 'mcp':
      return hint ? `MCP ${hint}` : 'MCP 工具';
    case 'team-build':
      return '创建团队';
    case 'team-send-message': {
      const recipient = delegationRecipientFromArgs(args);
      return recipient ? `派活给 ${recipient}` : '派活给成员';
    }
    case 'team-receive-message':
      return '成员回传';
    default:
      return formatToolName(toolName);
  }
}

export function toolStepIcon(kind: ToolKind): string {
  switch (kind) {
    case 'bash':
      return '▸';
    case 'web-search':
    case 'web-fetch':
    case 'mcp':
      return '🌐';
    case 'read':
      return '📄';
    case 'write':
      return '✎';
    case 'list':
      return '📁';
    case 'delete':
      return '🗑';
    case 'team-bus-publish':
      return '⇄';
    case 'team-build':
      return '👥';
    case 'team-send-message':
      return '✉';
    case 'team-receive-message':
      return '📨';
    default:
      return '•';
  }
}

export function bashCommandFromArgs(args: unknown): string | undefined {
  return readArgField(args, 'command');
}

export function writePreviewFromArgs(args: unknown): { path?: string; preview?: string } {
  const path = readArgField(args, 'path', 'file');
  const content = readArgField(args, 'content');
  const preview =
    content && content.length > 240 ? `${content.slice(0, 240)}…` : content;
  return { path, preview };
}

export function readPathFromArgs(args: unknown): string | undefined {
  return pathFromArgs(args);
}

export function busPublishPreviewFromArgs(args: unknown): { topic?: string; preview?: string } {
  const normalized = typeof args === 'string' ? (() => {
    try {
      return JSON.parse(args);
    } catch {
      return args;
    }
  })() : args;
  if (!normalized || typeof normalized !== 'object') {
    return {};
  }
  const record = normalized as Record<string, unknown>;
  const topic =
    typeof record.topic === 'string' && record.topic.trim() ? record.topic.trim() : undefined;
  const body = typeof record.body === 'string' ? record.body : undefined;
  const preview = body && body.length > 240 ? `${body.slice(0, 240)}…` : body;
  return { topic, preview };
}

export function busPublishTopicFromResult(result: unknown): string | undefined {
  if (!result || typeof result !== 'object') {
    return undefined;
  }
  const record = result as Record<string, unknown>;
  if (record.success === true && record.data && typeof record.data === 'object') {
    const data = record.data as Record<string, unknown>;
    return typeof data.topic === 'string' ? data.topic : undefined;
  }
  return undefined;
}

function toolResultData(result: unknown): Record<string, unknown> | undefined {
  if (!result || typeof result !== 'object') {
    return undefined;
  }
  const record = result as Record<string, unknown>;
  if (record.success === true && record.data && typeof record.data === 'object') {
    return record.data as Record<string, unknown>;
  }
  return record;
}

export function bashStdoutFromResult(result: unknown): string | undefined {
  const data = toolResultData(result);
  const stdout = data?.stdout;
  return typeof stdout === 'string' ? stdout : undefined;
}

export function parseFilePathsFromListResult(result: unknown, args?: unknown): string[] {
  const fromStdout = bashStdoutFromResult(result);
  if (fromStdout) {
    return fromStdout
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && !line.startsWith('total '));
  }
  const data = toolResultData(result);
  const files = data?.files ?? data?.paths ?? data?.entries;
  if (Array.isArray(files)) {
    return files
      .map((entry) => {
        if (typeof entry === 'string') {
          return entry;
        }
        if (entry && typeof entry === 'object') {
          const record = entry as Record<string, unknown>;
          const path = record.path ?? record.name ?? record.file;
          return typeof path === 'string' ? path : undefined;
        }
        return undefined;
      })
      .filter((path): path is string => Boolean(path));
  }
  const command = bashCommandFromArgs(args);
  if (command) {
    const tokens = command.trim().split(/\s+/).slice(1);
    return tokens.filter((token) => !token.startsWith('-'));
  }
  return [];
}

export function parseDeletePaths(args?: unknown, result?: unknown): string[] {
  const command = bashCommandFromArgs(args);
  if (command) {
    const tokens = command.trim().split(/\s+/).slice(1);
    return tokens.filter((token) => !token.startsWith('-') && token !== '-rf' && token !== '-r');
  }
  const data = toolResultData(result);
  const paths = data?.paths ?? data?.deleted;
  if (Array.isArray(paths)) {
    return paths.filter((path): path is string => typeof path === 'string');
  }
  const single = pathFromArgs(args);
  return single ? [single] : [];
}

export interface WriteDiffSummary {
  path: string;
  label: string;
  isNew: boolean;
}

export function writeDiffSummary(args?: unknown, result?: unknown): WriteDiffSummary | null {
  const { path, preview } = writePreviewFromArgs(args);
  if (!path) {
    return null;
  }
  const data = toolResultData(result);
  const bytes = typeof data?.bytes === 'number' ? data.bytes : undefined;
  const contentLines = preview ? preview.split('\n').length : 0;
  const isNew = contentLines > 0 && (bytes == null || bytes <= (preview?.length ?? 0) + 64);
  if (isNew) {
    const suffix = bytes != null ? ` · ${bytes} B` : contentLines > 0 ? ` · ${contentLines} 行` : '';
    return { path, isNew: true, label: `新建 ${path}${suffix}` };
  }
  const added = contentLines;
  const suffix = bytes != null ? ` · ${bytes} B` : '';
  if (added > 0) {
    return { path, isNew: false, label: `+${added} · ${path}${suffix}` };
  }
  return { path, isNew: false, label: `已写入 ${path}${suffix}` };
}

export interface WebSearchHit {
  title: string;
  url: string;
  snippet?: string;
}

export interface WebFetchPreview {
  url: string;
  title?: string;
  snippet?: string;
}

function readStringField(source: Record<string, unknown>, ...keys: string[]): string | undefined {
  for (const key of keys) {
    const value = source[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return undefined;
}

function normalizeWebSearchHit(entry: unknown): WebSearchHit | null {
  if (!entry || typeof entry !== 'object') {
    return null;
  }
  const record = entry as Record<string, unknown>;
  const url = readStringField(record, 'url', 'link', 'href');
  const title = readStringField(record, 'title', 'name', 'heading') ?? url;
  if (!url || !title) {
    return null;
  }
  return {
    title,
    url,
    snippet: readStringField(record, 'snippet', 'description', 'summary', 'excerpt'),
  };
}

export function webSearchQueryFromArgs(args?: unknown): string | undefined {
  if (!args || typeof args !== 'object') {
    return undefined;
  }
  const record = args as Record<string, unknown>;
  return readStringField(record, 'query', 'q', 'search', 'keyword');
}

export function webFetchUrlFromArgs(args?: unknown): string | undefined {
  if (!args || typeof args !== 'object') {
    return undefined;
  }
  const record = args as Record<string, unknown>;
  return readStringField(record, 'url', 'uri', 'link');
}

export function parseWebSearchResults(result?: unknown, args?: unknown): WebSearchHit[] {
  const data = toolResultData(result);
  const rawResults = data?.results ?? data?.items ?? data?.hits ?? (Array.isArray(result) ? result : undefined);
  if (Array.isArray(rawResults)) {
    return rawResults
      .map((entry) => normalizeWebSearchHit(entry))
      .filter((hit): hit is WebSearchHit => hit != null);
  }
  const query = webSearchQueryFromArgs(args);
  if (query && data) {
    const url = readStringField(data, 'url', 'link');
    if (url) {
      return [{ title: readStringField(data, 'title') ?? url, url, snippet: readStringField(data, 'snippet') }];
    }
  }
  return [];
}

export function parseWebFetchPreview(result?: unknown, args?: unknown): WebFetchPreview | null {
  const data = toolResultData(result);
  const url = webFetchUrlFromArgs(args) ?? (data ? readStringField(data, 'url', 'uri', 'link') : undefined);
  if (!url) {
    return null;
  }
  const content = data ? readStringField(data, 'content', 'text', 'body', 'markdown') : undefined;
  const title = data ? readStringField(data, 'title', 'name') : undefined;
  const snippet =
    (data ? readStringField(data, 'snippet', 'summary', 'description', 'excerpt') : undefined)
    ?? (content && content.length > 280 ? `${content.slice(0, 280)}…` : content);
  return { url, title, snippet };
}

/** mcp__qieman__SearchFunds → { server: qieman, tool: SearchFunds } */
export function parseMcpToolName(toolName: string): { server?: string; tool?: string } {
  const base = formatToolName(toolName);
  const parts = base.split('__');
  if (parts.length >= 3 && parts[0] === 'mcp') {
    return { server: parts[1], tool: parts.slice(2).join('__') };
  }
  if (parts.length >= 2 && base.includes('mcp')) {
    return { server: parts[0], tool: parts.slice(1).join('__') };
  }
  return {};
}

export interface SkillPreview {
  name: string;
  detail?: string;
}

export interface SkillProgress {
  percent?: number;
  message?: string;
}

function readSkillField(args: unknown, ...keys: string[]): string | undefined {
  if (!args || typeof args !== 'object') {
    return undefined;
  }
  const record = args as Record<string, unknown>;
  for (const key of keys) {
    const value = record[key];
    if (typeof value === 'string' && value.trim()) {
      return value.trim();
    }
  }
  return undefined;
}

export function parseSkillPreview(args: unknown, toolName: string): SkillPreview {
  const name =
    readSkillField(args, 'skillName', 'skill_name', 'skillId', 'skill_id', 'name', 'id')
    ?? formatToolName(toolName).replace(/^skill(__)?/i, '').replace(/__/g, '/')
    ?? formatToolName(toolName);
  const detail = readSkillField(args, 'step', 'phase', 'action', 'task', 'description');
  return { name: name || '技能', detail };
}

export function skillProgressFromResult(result?: unknown): SkillProgress | null {
  const data = toolResultData(result);
  if (!data && result == null) {
    return null;
  }
  const record = (data ?? result) as Record<string, unknown>;
  const percentRaw = record.percent ?? record.progress ?? record.percentComplete;
  const percent = typeof percentRaw === 'number' ? percentRaw : undefined;
  const message = readStringField(record, 'message', 'status', 'summary', 'detail');
  if (percent == null && !message) {
    if (record.success === true) {
      return { message: '已完成' };
    }
    if (record.success === false) {
      return { message: readStringField(record, 'error') ?? '执行失败' };
    }
    return null;
  }
  return { percent, message };
}
