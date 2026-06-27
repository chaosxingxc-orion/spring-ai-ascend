# Excel 文件处理

Use workspace tools to read/write CSV or tabular text exports when xlsx binaries are not available.

## Capabilities

- Parse tab-separated or CSV files in the session workspace
- Generate markdown tables summarizing spreadsheet data
- Write structured outputs as `.csv` or `.md` for downstream tools

## SOP

1. Confirm the target file path under the session workspace
2. Read with the read tool; avoid assuming binary xlsx parsing in MVP
3. Write summaries to `excel-summary.md` or export CSV as needed
