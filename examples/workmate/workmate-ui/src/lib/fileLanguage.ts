export function languageFromPath(path: string, mime?: string): string {
  const ext = path.split('/').pop()?.split('.').pop()?.toLowerCase() ?? '';
  const mimeMap: Record<string, string> = {
    'text/markdown': 'markdown',
    'text/html': 'html',
    'application/json': 'json',
    'text/javascript': 'javascript',
    'application/typescript': 'typescript',
  };
  if (mime && mimeMap[mime]) {
    return mimeMap[mime];
  }
  const extMap: Record<string, string> = {
    md: 'markdown',
    markdown: 'markdown',
    html: 'html',
    htm: 'html',
    json: 'json',
    ts: 'typescript',
    tsx: 'typescript',
    js: 'javascript',
    jsx: 'javascript',
    css: 'css',
    yaml: 'yaml',
    yml: 'yaml',
    sh: 'bash',
    py: 'python',
  };
  return extMap[ext] ?? 'plain';
}

export function isMarkdownPath(path: string, mime?: string): boolean {
  return languageFromPath(path, mime) === 'markdown';
}

export function isHtmlPath(path: string, mime?: string): boolean {
  return languageFromPath(path, mime) === 'html';
}

const IMAGE_EXTENSIONS = new Set(['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'ico']);
const PDF_EXTENSIONS = new Set(['pdf']);
const VIDEO_EXTENSIONS = new Set(['mp4', 'webm', 'mov', 'm4v']);
const OFFICE_EXTENSIONS = new Set(['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx']);

function extensionOf(path: string): string {
  return path.split('/').pop()?.split('.').pop()?.toLowerCase() ?? '';
}

function mimeIncludes(mime: string | undefined, token: string): boolean {
  return Boolean(mime && mime.toLowerCase().includes(token));
}

export function isImagePath(path: string, mime?: string): boolean {
  return IMAGE_EXTENSIONS.has(extensionOf(path)) || mimeIncludes(mime, 'image/');
}

export function isPdfPath(path: string, mime?: string): boolean {
  return PDF_EXTENSIONS.has(extensionOf(path)) || mimeIncludes(mime, 'pdf');
}

export function isVideoPath(path: string, mime?: string): boolean {
  return VIDEO_EXTENSIONS.has(extensionOf(path)) || mimeIncludes(mime, 'video/');
}

export function isOfficePath(path: string, mime?: string): boolean {
  const ext = extensionOf(path);
  if (OFFICE_EXTENSIONS.has(ext)) {
    return true;
  }
  if (!mime) {
    return false;
  }
  const lower = mime.toLowerCase();
  return lower.includes('wordprocessingml')
    || lower.includes('spreadsheetml')
    || lower.includes('presentationml')
    || lower.includes('msword')
    || lower.includes('ms-excel')
    || lower.includes('ms-powerpoint');
}

/** Binary preview via GET .../preview/ — skip text readFile. */
export function isBinaryPreviewPath(path: string, mime?: string): boolean {
  return isImagePath(path, mime)
    || isPdfPath(path, mime)
    || isVideoPath(path, mime)
    || isOfficePath(path, mime);
}

export function previewKind(
  path: string,
  mime?: string,
): 'image' | 'pdf' | 'video' | 'office' | 'unsupported-binary' | null {
  if (isImagePath(path, mime)) {
    return 'image';
  }
  if (isPdfPath(path, mime)) {
    return 'pdf';
  }
  if (isVideoPath(path, mime)) {
    return 'video';
  }
  if (isOfficePath(path, mime)) {
    return 'office';
  }
  if (isBinaryPreviewPath(path, mime)) {
    return 'unsupported-binary';
  }
  return null;
}
