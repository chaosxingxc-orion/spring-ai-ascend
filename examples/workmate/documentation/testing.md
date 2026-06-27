# Testing

WorkMate uses **unit tests** for pure logic and **MockMvc integration tests** for HTTP
controllers. Prefer unit tests when Spring context is not required.

## Layout

| Layer | Location | When to use |
|-------|----------|-------------|
| Unit | `src/test/java/**/**Test.java` | Services, projectors, orchestrators with mocks |
| Integration | Same tree, often `*Controller*Test` | End-to-end HTTP + JPA with H2 in-memory |
| UI | `workmate-ui/src/**/*.test.ts` | Vitest for lib/components |

## Integration test baseline

Use `WorkmateIntegrationTestBase` + `WorkmateTestProperties` instead of copying H2/Flyway
boilerplate:

```java
@SpringBootTest  // inherited from base
@AutoConfigureMockMvc  // inherited
class ExampleControllerTest extends WorkmateIntegrationTestBase {

    private static WorkmateTestPaths paths;

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) throws Exception {
        paths = WorkmateTestProperties.registerBaseline(registry, "example-db");
        WorkmateTestProperties.registerOfficeRoot(registry); // when office/ is needed
    }
}
```

Studio controller tests share `StudioControllerTestSupport` for draft reset and zip helpers.

## Large controller suites

Studio and session controller tests are split by concern:

- `StudioControllerCatalogTest` — read/list/reload
- `StudioControllerExpertApiTest` — expert write, validate, dry-run, publish
- `StudioControllerAssetsApiTest` — skills, teams, welcome, playbooks, import/export
- `SessionControllerLifecycleTest` — create/list/plan confirm
- `SessionControllerMutationTest` — metadata, chat, ACP ingest, attachments

## Team orchestrator contract

`TeamOrchestratorTopologyContractTest` runs a minimal routing contract across coordination
topologies (orchestrator, pipeline, message-bus, shared-state) using mocked backends.

## Timeline single source of truth

**Backend:** `run_events` table is authoritative. `SessionPersistenceService` and audit
projectors derive chat items and ledger entries — no dual-write.

**Frontend:** On session open, chat is hydrated from server projections (`sessionChatLoad`).
Live updates arrive via SSE; team sessions may incrementally poll `run_events`. Client-side
`*Hydrate` helpers only fill gaps during streaming — they must not diverge from server shape.

See [architecture.md](./architecture.md) for the event-sourcing overview.

## Running tests

```bash
# From examples/workmate/
make test          # backend + frontend

# Backend only (from workmate-api/)
../../../mvnw test

# Frontend only (from workmate-ui/)
npm test
```
