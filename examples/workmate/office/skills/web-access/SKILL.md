# Web Access

Guidance for fetching external information safely into the session workspace.

## Capabilities

- Use MCP tools when configured (e.g. fund search, filesystem docs)
- Summarize external results into workspace markdown files
- Never exfiltrate secrets from the workspace

## SOP

1. Prefer MCP allowlisted tools for external reads
2. Write results into the session workspace with the write tool
3. Cite sources in the output markdown
