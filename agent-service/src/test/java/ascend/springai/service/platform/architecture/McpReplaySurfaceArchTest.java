package ascend.springai.service.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces ARCHITECTURE.md §4 #59 (MCP-only telemetry replay surface).
 *
 * <p>Trace replay and run/session listing MUST be exposed exclusively via MCP
 * tools (W4: {@code get_run_trace}, {@code list_runs}, {@code get_llm_call},
 * {@code list_sessions} per ADR-0017). HTTP/REST endpoints, UI controllers, or
 * direct DB read endpoints are forbidden — they would re-introduce the Admin UI
 * surface that §1 explicitly excludes.
 *
 * <p>The rule lives in {@code agent-service} (consolidated from the pre-Phase-C
 * {@code agent-platform} module per ADR-0078) because that is where
 * {@code @RestController} classes reside in this monorepo. The runtime kernel
 * (consolidated from pre-Phase-C {@code agent-runtime}; shared types now live in
 * {@code agent-runtime-core} per ADR-0079) hosts no HTTP endpoints.
 *
 * <p>Vacuous at L1.x (no class lives under {@code web.replay}, {@code web.trace},
 * or {@code web.session}); arms automatically if a future PR adds one.
 *
 * <p>Enforcer E46. ADR-0017, §1 exclusion.
 */
class McpReplaySurfaceArchTest {

    private static final JavaClasses PLATFORM_MAIN_CLASSES = new ClassFileImporter()
            .importPackages("ascend.springai.service.platform");

    @Test
    void no_rest_controller_lives_under_replay_or_trace_or_session_web_packages() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "ascend.springai.service.platform.web.replay..",
                        "ascend.springai.service.platform.web.trace..",
                        "ascend.springai.service.platform.web.session..")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                .allowEmptyShould(true);
        rule.check(PLATFORM_MAIN_CLASSES);
    }
}
