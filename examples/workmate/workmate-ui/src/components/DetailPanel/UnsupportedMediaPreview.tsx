interface UnsupportedMediaPreviewProps {
  path: string;
  kind: 'office' | 'binary';
}

/** W39-C4/C5 — Office 或未支持二进制格式的友好提示。 */
export function UnsupportedMediaPreview({ path, kind }: UnsupportedMediaPreviewProps) {
  const message = kind === 'office'
    ? 'Office 文档（Word / Excel / PowerPoint）暂不支持内联预览。请下载后在本地打开，或导出为 PDF / HTML 后再预览。'
    : '该文件格式暂不支持内联预览。请下载后使用本地应用打开。';
  return (
    <div className="media-preview media-preview-unsupported">
      <p className="media-preview-unsupported-path" title={path}>{path}</p>
      <p className="media-preview-unsupported-msg muted">{message}</p>
    </div>
  );
}
