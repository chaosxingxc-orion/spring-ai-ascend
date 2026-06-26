# Web Search SOP

Skill for the deep-research search sub-agent: how to issue web_search queries that maximise official-source hit-rate while keeping the call count to one.

## When to apply

The root DeepAgent dispatches a natural-language question. Treat it as a single search task — no clarification, no follow-up dispatch.

## Step 1 — Detect query language and locale hints

- If the question contains CJK characters, set `language: "zh"`.
- If the question contains only Latin script, set `language: "en"`.
- Mixed: prefer the script of the proper nouns; default to `"zh"` for product names that originate in Chinese (豆包, 通义, 文心, etc.).

## Step 2 — Choose `time_range`

- Question mentions "最近"/"latest"/"2026"/this year → `time_range: "month"`.
- Question references a known release window (e.g. "DeepSeek V3.1 发布") → `time_range: "year"`.
- General factual lookup → `time_range: "all"`.

## Step 3 — Build the query string

- Pass through verbatim **first**. Tavily already understands natural language.
- Keep one vendor brand name if present; strip generic prefixes like "请问"/"can you tell me".
- Quote multi-word phrases only when the question demands an exact phrase ("agent runtime" not agent runtime).

## Step 4 — Pick `top_k`

- Comparison / list questions → `top_k: 8`.
- Single-fact lookup (a version number, a release date) → `top_k: 5`.
- Never exceed 10 — Tavily charges per result and the downstream verifier deduplicates anyway.

## Step 5 — Retry rule

Call exactly once. Retry **only** if `results` is empty. Retry strategy:

1. Drop the vendor brand name from the query.
2. Switch `language` to `"any"`.
3. Do not change `top_k` or `time_range`.

Never retry a non-empty result. Never make two calls in a single turn.

## Output contract reminder

Return the tool's JSON output **as-is**. Do not rewrite snippets, do not invent fields, do not synthesise summaries. The root agent owns verification and synthesis.