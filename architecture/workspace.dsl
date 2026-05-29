// Spring AI Ascend Architecture Workspace
//
// PRIMARY ARCHITECTURE AUTHORITY — workspace.dsl is the sole main entry
// (唯一主入口) for the entire architecture design system.
//
// Authority: ADR-0147 + ADR-0149 + ADR-0150.
// Migration plan: see ADR-0147 for the Structurizr workspace authority migration.
// Status: W0-W7 shipped on main (commit 552ca4e and downstream).
//          W8 docs consolidation lands on top — L1 corpus moved to
//          architecture/docs/L1/, module-root ARCHITECTURE.md files
//          merged into the L1 corpus, `!docs` + `!adrs` directives wired.
//
// What this workspace carries (per ADR-0150):
//   System / Container / Component     -> L1 architecture structure (8 Maven modules)
//   Custom Element + Tags + Properties -> Feature / Capability / FunctionPoint instances
//   Relationship + saa.rel             -> dependency / implements / verifies / constrains edges
//   Documentation (!docs)              -> human-reviewable Markdown imported from architecture/docs/
//   ADR (!adrs)                        -> decision-source Markdown imported from architecture/decisions/
//   Views {}                           -> 4+1 organisation form (logical/development/process/physical/scenarios)
//
// DSL conventions (per architecture/README.md):
//   * `element` is the keyword for custom elements (NOT `customElement`).
//   * `Model.getElements()` includes CustomElements; validator walks it once.
//   * Identifier scope is flat (top-level globally unique).
//   * Profile-tagged elements MUST satisfy required-properties.yaml.

workspace "Spring AI Ascend" "Architecture authority workspace (W8 — Structurizr-first; docs consolidated)" {

    !docs docs
    !adrs decisions

    model {
        springAiAscend = softwareSystem "Spring AI Ascend" "Agent runtime platform" "SAA Module" {
            properties {
                "saa.id" "SYS-SPRING-AI-ASCEND"
                "saa.kind" "software_system"
                "saa.level" "L0"
                "saa.view" "logical"
                "saa.status" "shipped"
                "saa.owner" "architecture"
            }

            agentClient = container "agent-client" "Client SDK and edge-plane API owner" "Java 21 / Maven" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-CLIENT"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-client"
                }
            }

            agentBus = container "agent-bus" "Bus & State Hub plane — ingress + s2c + three-track channel isolation" "Java 21 / Maven" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-BUS"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-bus"
                }
            }

            agentService = container "agent-service" "Northbound facade, runtime API, tenant/idempotency, run orchestration" "Java 21 / Spring Boot" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-SERVICE"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-service"
                }
            }

            agentExecutionEngine = container "agent-execution-engine" "Engine adapter + orchestration SPIs (EngineRegistry, EngineEnvelope)" "Java 21 / Maven" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-EXECUTION-ENGINE"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-execution-engine"
                }
            }

            agentMiddleware = container "agent-middleware" "RuntimeMiddleware SPI + hook dispatch" "Java 21 / Maven" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-MIDDLEWARE"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-middleware"
                }
            }

            agentEvolve = container "agent-evolve" "Evolution-plane boundary (skeleton; W3+)" "Java 21 / Maven" "SAA Module" {
                properties {
                    "saa.id" "MOD-AGENT-EVOLVE"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "agent-evolve"
                }
            }

            graphMemoryStarter = container "spring-ai-ascend-graphmemory-starter" "GraphMemory Spring Boot starter" "Java 21 / Spring Boot starter" "SAA Module" {
                properties {
                    "saa.id" "MOD-GRAPHMEMORY-STARTER"
                    "saa.kind" "module"
                    "saa.level" "L1"
                    "saa.view" "development"
                    "saa.status" "shipped"
                    "saa.owner" "spring-ai-ascend-graphmemory-starter"
                }
            }
        }

        // Authored zone — W2 mount of capabilities/function-points + W9 mount of features.
        !include features/capabilities.dsl
        !include features/function-points.dsl
        !include features/features.dsl
        !include features/verification.dsl

        // Generated zone — W3 emitters. NEVER hand-edit these files.
        // Re-emit via: java ... com.huawei.ascend.tools.architecture.fragment.AllFragmentsCli --repo .
        // Idempotency enforced by Wave 5 blocking gate.
        //
        // Inclusion ORDER is significant: a fragment referencing another
        // fragment's identifier must be included AFTER that fragment.
        //   modules    -> defines genModule_*
        //   spi-catalog -> references genModule_* (declares_spi edges)
        //   rules      -> defines rule_*
        //   principles -> references rule_* (operationalised_by edges)
        //   enforcers  -> defines enforcer_* (independent)
        //   adr-graph  -> defines adr_* (self-referencing edges within file)
        //   surface-classification -> defines surface_* (independent)
        !include generated/modules.dsl
        !include generated/spi-catalog.dsl
        !include generated/rules.dsl
        !include generated/principles.dsl
        !include generated/enforcers.dsl
        !include generated/adr-graph.dsl
        !include generated/surface-classification.dsl

        // Authored zone — structural axis (ADR-0157). Included LAST so frame edges
        // to genModule_* / fp* / feat* resolve against already-declared elements.
        !include features/engineering-frames.dsl
    }

    views {
        !include views/L0-system-context.dsl
        !include views/L1-development.dsl
        !include views/L1-process.dsl
        !include views/L1-physical.dsl
        !include views/L1-scenarios.dsl
    }
}
