interface DevStudioListPaginationProps {
  page: number;
  pageSize: number;
  totalItems: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

const PAGE_SIZE_OPTIONS = [20, 50, 100] as const;

export function DevStudioListPagination({
  page,
  pageSize,
  totalItems,
  onPageChange,
  onPageSizeChange,
}: DevStudioListPaginationProps) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  const safePage = Math.min(Math.max(1, page), totalPages);
  const start = totalItems === 0 ? 0 : (safePage - 1) * pageSize + 1;
  const end = totalItems === 0 ? 0 : Math.min(totalItems, safePage * pageSize);

  return (
    <div className="dev-studio-pagination">
      <p className="muted dev-studio-pagination-summary">
        {totalItems === 0 ? '共 0 条' : `第 ${start}–${end} 条，共 ${totalItems} 条`}
      </p>
      <div className="dev-studio-pagination-controls">
        <label className="dev-studio-pagination-size">
          每页
          <select
            value={pageSize}
            onChange={(event) => onPageSizeChange(Number.parseInt(event.target.value, 10))}
            aria-label="每页条数"
          >
            {PAGE_SIZE_OPTIONS.map((size) => (
              <option key={size} value={size}>
                {size}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className="btn ghost sm"
          disabled={safePage <= 1}
          onClick={() => onPageChange(safePage - 1)}
        >
          上一页
        </button>
        <span className="dev-studio-pagination-page">
          {safePage} / {totalPages}
        </span>
        <button
          type="button"
          className="btn ghost sm"
          disabled={safePage >= totalPages}
          onClick={() => onPageChange(safePage + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  );
}
