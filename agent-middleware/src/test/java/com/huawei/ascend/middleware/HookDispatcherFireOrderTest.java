package com.huawei.ascend.middleware;

import com.huawei.ascend.middleware.spi.HookContext;
import com.huawei.ascend.middleware.spi.HookOutcome;
import com.huawei.ascend.middleware.spi.HookPoint;
import com.huawei.ascend.middleware.spi.RuntimeMiddleware;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies HookDispatcher ordering + outcome aggregation per
 * {@code docs/contracts/engine-hooks.v1.yaml}.
 *
 * <ul>
 *   <li>before-X / on-X hooks fire in registration order, fail_fast.</li>
 *   <li>after-X hooks fire in reverse registration order (LIFO unwind).</li>
 *   <li>on_error fires the full chain best_effort and aggregates.</li>
 *   <li>A throwing middleware is converted to {@code Fail} - never crashes the dispatcher.</li>
 * </ul>
 *
 * <p>Authority: ADR-0073.
 */
class HookDispatcherFireOrderTest {

    private static HookContext ctx(HookPoint point) {
        return HookContext.of(point, UUID.randomUUID(), "tenant-A");
    }

    @Test
    void empty_dispatcher_proceeds() {
        HookDispatcher d = HookDispatcher.empty();

        HookOutcome outcome = d.fire(ctx(HookPoint.BEFORE_SUSPENSION));

        assertThat(outcome).isInstanceOf(HookOutcome.Proceed.class);
    }

    @Test
    void before_hook_fires_in_registration_order_until_first_non_proceed() {
        List<String> seen = new ArrayList<>();
        RuntimeMiddleware m1 = c -> { seen.add("m1"); return HookOutcome.proceed(); };
        RuntimeMiddleware m2 = c -> { seen.add("m2"); return new HookOutcome.Fail("blocked"); };
        RuntimeMiddleware m3 = c -> { seen.add("m3"); return HookOutcome.proceed(); };

        HookDispatcher d = new HookDispatcher(List.of(m1, m2, m3));

        HookOutcome outcome = d.fire(ctx(HookPoint.BEFORE_LLM_INVOCATION));

        assertThat(seen).containsExactly("m1", "m2");        // m3 never fired
        assertThat(outcome).isInstanceOfSatisfying(HookOutcome.Fail.class,
                f -> assertThat(f.reason()).isEqualTo("blocked"));
    }

    @Test
    void after_hook_fires_in_reverse_registration_order() {
        List<String> seen = new ArrayList<>();
        RuntimeMiddleware m1 = c -> { seen.add("m1"); return HookOutcome.proceed(); };
        RuntimeMiddleware m2 = c -> { seen.add("m2"); return HookOutcome.proceed(); };
        RuntimeMiddleware m3 = c -> { seen.add("m3"); return HookOutcome.proceed(); };

        HookDispatcher d = new HookDispatcher(List.of(m1, m2, m3));
        d.fire(ctx(HookPoint.AFTER_LLM_INVOCATION));

        assertThat(seen).containsExactly("m3", "m2", "m1");
    }

    @Test
    void on_error_fires_full_chain_best_effort() {
        List<String> seen = new ArrayList<>();
        RuntimeMiddleware m1 = c -> { seen.add("m1"); return new HookOutcome.Fail("first-fail"); };
        RuntimeMiddleware m2 = c -> { seen.add("m2"); return HookOutcome.proceed(); };
        RuntimeMiddleware m3 = c -> { seen.add("m3"); return new HookOutcome.Fail("third-fail"); };

        HookDispatcher d = new HookDispatcher(List.of(m1, m2, m3));
        HookOutcome outcome = d.fire(ctx(HookPoint.ON_ERROR));

        assertThat(seen).containsExactly("m1", "m2", "m3");   // ALL fired
        assertThat(outcome).isInstanceOfSatisfying(HookOutcome.Fail.class,
                f -> assertThat(f.reason()).isEqualTo("first-fail"));   // first non-Proceed wins
    }

    @Test
    void throwing_middleware_is_converted_to_fail_outcome() {
        List<String> seen = new ArrayList<>();
        RuntimeMiddleware exploder = c -> { seen.add("exploder"); throw new RuntimeException("boom"); };
        RuntimeMiddleware downstream = c -> { seen.add("downstream"); return HookOutcome.proceed(); };

        HookDispatcher d = new HookDispatcher(List.of(exploder, downstream));
        HookOutcome outcome = d.fire(ctx(HookPoint.BEFORE_SUSPENSION));

        assertThat(seen).containsExactly("exploder");
        assertThat(outcome).isInstanceOfSatisfying(HookOutcome.Fail.class,
                f -> assertThat(f.reason()).contains("middleware_threw"));
    }
}
