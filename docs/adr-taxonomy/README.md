# ADR Level/Module Taxonomy

This directory provides a practical **ADR grading (L0/L1/L2)** and **module ownership mapping** for all current ADR files.

## Grouped matrix

| Level | Module | ADR count | ADR IDs |
|---|---|---:|---|
| L0 | governance-core | 19 | 0006, 0019, 0025, 0033, 0035, 0038, 0039, 0042, 0043, 0044, 0045, 0046, 0047, 0059, 0064, 0065, 0066, 0067, 0068 |
| L1 | agent-service-runtime | 10 | 0001, 0003, 0020, 0027, 0032, 0050, 0054, 0055, 0057, 0062 |
| L1 | cross-cutting-domain | 21 | 0008, 0012, 0015, 0016, 0023, 0024, 0028, 0029, 0031, 0034, 0036, 0037, 0040, 0041, 0048, 0049, 0051, 0052, 0053, 0058, 0060 |
| L1 | data-persistence | 3 | 0004, 0007, 0011 |
| L1 | module-contracts | 4 | 0014, 0021, 0022, 0030 |
| L1 | observability | 4 | 0009, 0017, 0061, 0063 |
| L1 | security-isolation | 5 | 0005, 0010, 0013, 0018, 0056 |
| L1 | spring-ai-integration | 1 | 0002 |
| L2 | agent-service-runtime | 15 | 0070, 0071, 0072, 0073, 0076, 0079, 0088, 0090, 0100, 0106, 0107, 0112, 0135, 0136, 0137 |
| L2 | cross-cutting-domain | 34 | 0069, 0074, 0075, 0078, 0081, 0086, 0087, 0089, 0091, 0092, 0093, 0094, 0095, 0096, 0097, 0098, 0099, 0101, 0102, 0103, 0105, 0109, 0110, 0113, 0114, 0115, 0116, 0117, 0118, 0119, 0120, 0128, 0138, 0139 |
| L2 | data-persistence | 2 | 0077, 0082 |
| L2 | module-contracts | 5 | 0080, 0104, 0123, 0126, 0130 |
| L2 | security-isolation | 2 | 0108, 0111 |
| L2 | spring-ai-integration | 10 | 0121, 0122, 0124, 0125, 0127, 0129, 0131, 0132, 0133, 0134 |

## Full inventory

| ADR | Level | View | Module | File |
|---|---|---|---|---|
| 0001 | L1 | development | agent-service-runtime | `docs/adr/locked/0001-java-21-spring-boot-runtime.md` |
| 0002 | L1 | logical | spring-ai-integration | `docs/adr/locked/0002-spring-ai-llm-gateway.md` |
| 0003 | L1 | process | agent-service-runtime | `docs/adr/0003-temporal-durable-workflows.md` |
| 0004 | L1 | physical | data-persistence | `docs/adr/locked/0004-postgres-primary-data-store.md` |
| 0005 | L1 | physical | security-isolation | `docs/adr/locked/0005-tenant-isolation-guc-set-local.md` |
| 0006 | L0 | process | governance-core | `docs/adr/locked/0006-posture-model-dev-research-prod.md` |
| 0007 | L1 | logical | data-persistence | `docs/adr/0007-outbox-postgres-not-kafka.md` |
| 0008 | L1 | process | cross-cutting-domain | `docs/adr/0008-resilience4j-circuit-breaker.md` |
| 0009 | L1 | development | observability | `docs/adr/0009-micrometer-observability.md` |
| 0010 | L1 | process | security-isolation | `docs/adr/locked/0010-spring-security-oauth2.md` |
| 0011 | L1 | development | data-persistence | `docs/adr/locked/0011-flyway-schema-migration.md` |
| 0012 | L1 | development | cross-cutting-domain | `docs/adr/0012-valkey-session-cache.md` |
| 0013 | L1 | physical | security-isolation | `docs/adr/0013-vault-secrets-management.md` |
| 0014 | L1 | logical | module-contracts | `docs/adr/locked/0014-contract-spine-versioning-policy.md` |
| 0015 | L1 | logical | cross-cutting-domain | `docs/adr/locked/0015-layered-architecture-capability-model.md` |
| 0016 | L1 | process | cross-cutting-domain | `docs/adr/0016-a2a-federation-strategic-deferral.md` |
| 0017 | L1 | logical | observability | `docs/adr/0017-dev-time-trace-replay.md` |
| 0018 | L1 | physical | security-isolation | `docs/adr/0018-sandbox-executor-spi.md` |
| 0019 | L0 | scenarios | governance-core | `docs/adr/0019-suspend-signal-and-suspend-reason-taxonomy.md` |
| 0020 | L1 | process | agent-service-runtime | `docs/adr/locked/0020-runlifecycle-spi-and-runstatus-formal-dfa.md` |
| 0021 | L1 | development | module-contracts | `docs/adr/0021-layered-spi-taxonomy.md` |
| 0022 | L1 | process | module-contracts | `docs/adr/0022-payload-codec-spi.md` |
| 0023 | L1 | process | cross-cutting-domain | `docs/adr/0023-cross-boundary-context-propagation.md` |
| 0024 | L1 | logical | cross-cutting-domain | `docs/adr/0024-suspension-write-atomicity.md` |
| 0025 | L0 | scenarios | governance-core | `docs/adr/0025-checkpoint-ownership-boundary.md` |
| 0027 | L1 | logical | agent-service-runtime | `docs/adr/locked/0027-idempotency-scope-w0-header-validation.md` |
| 0028 | L1 | logical | cross-cutting-domain | `docs/adr/0028-causal-payload-envelope-and-semantic-ontology.md` |
| 0029 | L1 | logical | cross-cutting-domain | `docs/adr/0029-cognition-action-separation.md` |
| 0030 | L1 | process | module-contracts | `docs/adr/0030-skill-spi-lifecycle-resource-matrix.md` |
| 0031 | L1 | physical | cross-cutting-domain | `docs/adr/0031-three-track-channel-isolation.md` |
| 0032 | L1 | logical | agent-service-runtime | `docs/adr/0032-scope-based-run-hierarchy-and-planner-contract-minimal.md` |
| 0033 | L0 | process | governance-core | `docs/adr/0033-logical-identity-equivalence-and-deployment-locus-vocabulary.md` |
| 0034 | L1 | process | cross-cutting-domain | `docs/adr/0034-memory-and-knowledge-taxonomy-at-l0.md` |
| 0035 | L0 | scenarios | governance-core | `docs/adr/0035-posture-enforcement-single-construction-path.md` |
| 0036 | L1 | process | cross-cutting-domain | `docs/adr/0036-contract-surface-truth-generalization.md` |
| 0037 | L1 | physical | cross-cutting-domain | `docs/adr/0037-wave-authority-consolidation.md` |
| 0038 | L0 | scenarios | governance-core | `docs/adr/0038-skill-spi-resource-tier-classification.md` |
| 0039 | L0 | scenarios | governance-core | `docs/adr/0039-payload-migration-adapter-strategy.md` |
| 0040 | L1 | process | cross-cutting-domain | `docs/adr/0040-w1-http-contract-reconciliation.md` |
| 0041 | L1 | logical | cross-cutting-domain | `docs/adr/0041-active-corpus-truth-sweep.md` |
| 0042 | L0 | scenarios | governance-core | `docs/adr/0042-test-evidence-enforcement-for-rule-25.md` |
| 0043 | L0 | scenarios | governance-core | `docs/adr/0043-active-normative-doc-catalog-and-peripheral-drift-prevention.md` |
| 0044 | L0 | scenarios | governance-core | `docs/adr/0044-spi-contract-precision-and-memory-metadata-reconciliation.md` |
| 0045 | L0 | scenarios | governance-core | `docs/adr/0045-shipped-row-evidence-path-existence-and-peripheral-wave-qualifier.md` |
| 0046 | L0 | scenarios | governance-core | `docs/adr/0046-release-note-shipped-surface-truth.md` |
| 0047 | L0 | scenarios | governance-core | `docs/adr/0047-active-entrypoint-truth-and-system-boundary-prose-convention.md` |
| 0048 | L1 | physical | cross-cutting-domain | `docs/adr/0048-service-layer-microservice-architecture-commitment.md` |
| 0049 | L1 | logical | cross-cutting-domain | `docs/adr/0049-c-s-dynamic-hydration-protocol.md` |
| 0050 | L1 | process | agent-service-runtime | `docs/adr/0050-workflow-intermediary-mailbox-rhythm-track.md` |
| 0051 | L1 | logical | cross-cutting-domain | `docs/adr/0051-memory-knowledge-ownership-boundary.md` |
| 0052 | L1 | process | cross-cutting-domain | `docs/adr/0052-skill-topology-scheduler-and-capability-bidding.md` |
| 0053 | L1 | process | cross-cutting-domain | `docs/adr/0053-cohesive-agent-swarm-execution.md` |
| 0054 | L1 | process | agent-service-runtime | `docs/adr/0054-long-connection-containment-runtime-handles.md` |
| 0055 | L1 | development | agent-service-runtime | `docs/adr/0055-permit-platform-to-runtime-direction.md` |
| 0056 | L1 | process | security-isolation | `docs/adr/0056-jwt-validation-and-tenant-claim-cross-check.md` |
| 0057 | L1 | process | agent-service-runtime | `docs/adr/0057-durable-idempotency-claim-replay.md` |
| 0058 | L1 | process | cross-cutting-domain | `docs/adr/0058-posture-boot-guard.md` |
| 0059 | L0 | scenarios | governance-core | `docs/adr/0059-code-as-contract-architectural-enforcement.md` |
| 0060 | L1 | scenarios | cross-cutting-domain | `docs/adr/0060-phase-l-reviewer-remediation.md` |
| 0061 | L1 | process | observability | `docs/adr/0061-telemetry-vertical-layer.md` |
| 0062 | L1 | logical | agent-service-runtime | `docs/adr/0062-trace-run-session-identity.md` |
| 0063 | L1 | development | observability | `docs/adr/0063-client-sdk-observability-contract.md` |
| 0064 | L0 | scenarios | governance-core | `docs/adr/0064-layer-0-governing-principles.md` |
| 0065 | L0 | scenarios | governance-core | `docs/adr/0065-competitive-baselines.md` |
| 0066 | L0 | development | governance-core | `docs/adr/0066-independent-module-evolution.md` |
| 0067 | L0 | development | governance-core | `docs/adr/0067-spi-dfx-tck-codesign.md` |
| 0068 | L0 | scenarios | governance-core | `docs/adr/0068-layered-4plus1-and-architecture-graph.yaml` |
| 0069 | L2 | logical | cross-cutting-domain | `docs/adr/0069-l0-ironclad-rules.yaml` |
| 0070 | L2 | logical | agent-service-runtime | `docs/adr/0070-cursor-flow-and-skill-capacity-runtime.yaml` |
| 0071 | L2 | logical | agent-service-runtime | `docs/adr/0071-engine-contract-structural-wave.yaml` |
| 0072 | L2 | logical | agent-service-runtime | `docs/adr/0072-engine-envelope-and-strict-matching.yaml` |
| 0073 | L2 | logical | agent-service-runtime | `docs/adr/0073-engine-hooks-and-runtime-middleware.yaml` |
| 0074 | L2 | logical | cross-cutting-domain | `docs/adr/0074-s2c-capability-callback.yaml` |
| 0075 | L2 | logical | cross-cutting-domain | `docs/adr/0075-evolution-scope-boundary.yaml` |
| 0076 | L2 | logical | agent-service-runtime | `docs/adr/0076-r2-pilot-runtime-self-validation.yaml` |
| 0077 | L2 | logical | data-persistence | `docs/adr/0077-schema-first-domain-contracts.yaml` |
| 0078 | L2 | logical | cross-cutting-domain | `docs/adr/0078-agent-service-consolidation.yaml` |
| 0079 | L2 | logical | agent-service-runtime | `docs/adr/0079-engine-extraction-runtime-core.yaml` |
| 0080 | L2 | logical | module-contracts | `docs/adr/0080-resilience-contract-spi-package-alignment.yaml` |
| 0081 | L2 | logical | cross-cutting-domain | `docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml` |
| 0082 | L2 | logical | data-persistence | `docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml` |
| 0086 | L2 | logical | cross-cutting-domain | `docs/adr/0086-rule-namespace-ratchet.yaml` |
| 0087 | L2 | logical | cross-cutting-domain | `docs/adr/0087-l0-rc12-authority-ratchet-and-deploy-truth.yaml` |
| 0088 | L2 | logical | agent-service-runtime | `docs/adr/0088-agent-runtime-core-dissolution.yaml` |
| 0089 | L2 | logical | cross-cutting-domain | `docs/adr/0089-edge-plane-ingress-gateway-mandate.yaml` |
| 0090 | L2 | logical | agent-service-runtime | `docs/adr/0090-rc14-cross-authority-parity-and-engine-semantic-home.yaml` |
| 0091 | L2 | logical | cross-cutting-domain | `docs/adr/0091-rc15-structural-carrier-parity-and-terminal-state-scope.yaml` |
| 0092 | L2 | logical | cross-cutting-domain | `docs/adr/0092-ledger-acknowledgment-and-agent-os-scope-boundary.yaml` |
| 0093 | L2 | logical | cross-cutting-domain | `docs/adr/0093-rc16-recurring-family-comprehensive-closure-and-meta-scope-completeness.yaml` |
| 0094 | L2 | logical | cross-cutting-domain | `docs/adr/0094-rc17-recurring-defect-family-truth-and-rule-consolidation.yaml` |
| 0095 | L2 | logical | cross-cutting-domain | `docs/adr/0095-rc18-comprehensive-hardening.yaml` |
| 0096 | L2 | logical | cross-cutting-domain | `docs/adr/0096-rc19-meta-recursion-permanent-close.yaml` |
| 0097 | L2 | logical | cross-cutting-domain | `docs/adr/0097-rc20-meta-recursion-actually-close-plus-d9.yaml` |
| 0098 | L2 | logical | cross-cutting-domain | `docs/adr/0098-rc21-scenario-phase-contracts-and-new-discipline-rules.yaml` |
| 0099 | L2 | logical | cross-cutting-domain | `docs/adr/0099-rc22-l1-architecture-depth-and-grounding.yaml` |
| 0100 | L2 | logical | agent-service-runtime | `docs/adr/0100-rc22-agent-service-l1-runtime-role-decomposition.yaml` |
| 0101 | L2 | logical | cross-cutting-domain | `docs/adr/0101-rc22-polymorphic-deployment-topology.yaml` |
| 0102 | L2 | logical | cross-cutting-domain | `docs/adr/0102-rc22-evolution-plane-online-offline-duality.yaml` |
| 0103 | L2 | logical | cross-cutting-domain | `docs/adr/0103-rc22-agent-middleware-naming-and-capability-services-distribution.yaml` |
| 0104 | L2 | logical | module-contracts | `docs/adr/0104-rc22-package-root-migration-to-com-huawei-ascend.yaml` |
| 0105 | L2 | logical | cross-cutting-domain | `docs/adr/0105-rc32-residual-corrective-family-truth-and-sanitizer-fix.yaml` |
| 0106 | L2 | logical | agent-service-runtime | `docs/adr/0106-run-version-two-phase-migration.yaml` |
| 0107 | L2 | logical | agent-service-runtime | `docs/adr/0107-federation-ancestor-reconstruction-via-runregistry.yaml` |
| 0108 | L2 | logical | security-isolation | `docs/adr/0108-tenant-reauth-widening-and-graph-isolation.yaml` |
| 0109 | L2 | logical | cross-cutting-domain | `docs/adr/0109-s2c-and-ingress-server-identity-proof.yaml` |
| 0110 | L2 | logical | cross-cutting-domain | `docs/adr/0110-audit-tamper-evidence-and-hook-pii-failsafe.yaml` |
| 0111 | L2 | logical | security-isolation | `docs/adr/0111-sandbox-routing-vault-rotation-otlp-tenant-outbox-replay.yaml` |
| 0112 | L2 | logical | agent-service-runtime | `docs/adr/0112-engine-stateless-executor-value-based-yield.yaml` |
| 0113 | L2 | logical | cross-cutting-domain | `docs/adr/0113-hook-ordering-failsafe-coherence-and-tie-break.yaml` |
| 0114 | L2 | logical | cross-cutting-domain | `docs/adr/0114-implementation-feasibility-batched-closures.yaml` |
| 0115 | L2 | logical | cross-cutting-domain | `docs/adr/0115-agent-service-l1-expansion-acceptance.yaml` |
| 0116 | L2 | logical | cross-cutting-domain | `docs/adr/0116-rc36-kernel-truth-and-cancel-cas-corrective.yaml` |
| 0117 | L2 | logical | cross-cutting-domain | `docs/adr/0117-rc37-ascend-kunpeng-strategic-repositioning.yaml` |
| 0118 | L2 | logical | cross-cutting-domain | `docs/adr/0118-rc38-audit-corrective-latent-correctness-and-deploy-packaging.yaml` |
| 0119 | L2 | logical | cross-cutting-domain | `docs/adr/0119-single-source-rendering.yaml` |
| 0120 | L2 | logical | cross-cutting-domain | `docs/adr/0120-brand-audience-b-alignment.yaml` |
| 0121 | L2 | logical | spring-ai-integration | `docs/adr/0121-model-gateway-spi.yaml` |
| 0122 | L2 | logical | spring-ai-integration | `docs/adr/0122-tool-skill-semantic-resolution.yaml` |
| 0123 | L2 | logical | module-contracts | `docs/adr/0123-memory-unified-spi.yaml` |
| 0124 | L2 | logical | spring-ai-integration | `docs/adr/0124-vector-retrieval-embedding-spi.yaml` |
| 0125 | L2 | logical | spring-ai-integration | `docs/adr/0125-spring-ai-integration-boundary.yaml` |
| 0126 | L2 | logical | module-contracts | `docs/adr/0126-planner-spi.yaml` |
| 0127 | L2 | logical | spring-ai-integration | `docs/adr/0127-skill-spi-tool-unification.yaml` |
| 0128 | L2 | logical | cross-cutting-domain | `docs/adr/0128-agent-first-class-entity.yaml` |
| 0129 | L2 | logical | spring-ai-integration | `docs/adr/0129-streaming-aware-model-gateway.yaml` |
| 0130 | L2 | logical | module-contracts | `docs/adr/0130-structured-output-converter-spi.yaml` |
| 0131 | L2 | logical | spring-ai-integration | `docs/adr/0131-prompt-template-spi.yaml` |
| 0132 | L2 | logical | spring-ai-integration | `docs/adr/0132-chat-advisor-spi.yaml` |
| 0133 | L2 | logical | spring-ai-integration | `docs/adr/0133-conversation-memory-spi-variant.yaml` |
| 0134 | L2 | logical | spring-ai-integration | `docs/adr/0134-tool-call-iteration-loop.yaml` |
| 0135 | L2 | logical | agent-service-runtime | `docs/adr/0135-agent-session-as-run-projection.yaml` |
| 0136 | L2 | logical | agent-service-runtime | `docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml` |
| 0137 | L2 | logical | agent-service-runtime | `docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml` |
| 0138 | L2 | logical | cross-cutting-domain | `docs/adr/0138-agent-service-five-layer-l1-ratification.yaml` |
| 0139 | L2 | logical | cross-cutting-domain | `docs/adr/0139-fast-slow-path-narrowed-semantics.yaml` |
