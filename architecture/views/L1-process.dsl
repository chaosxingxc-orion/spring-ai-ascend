// architecture/views/L1-process.dsl
//
// Authority: ADR-0151 (W3 of L1 Feature Registry plan).
// 4+1 process view — runtime/process model rendered as a container view
// with description text naming the key flows. Detailed dynamic-view
// sequences live in Markdown narrative (architecture/docs/L1/<module>/process.md).

container springAiAscend "L1-Process" "Process view — runtime/process model" {
    include *
    autoLayout lr
    title "Spring AI Ascend — L1 Process View"
    description "Process flows: (1) Run admission — POST /v1/runs through agent-service IdempotencyHeaderFilter + PostureBootGuard + agent-runtime EngineDispatcher. (2) Suspend/Resume — SuspendSignal causes Run→SUSPENDED; ResumeDispatcher transitions to RUNNING. (3) S2C Callback — agent-bus delivers callback envelope; agent-service ResumeDispatcher resumes. Containers above the line are runtime-active; everything is non-blocking I/O (Rule R-G + R-H)."
}
