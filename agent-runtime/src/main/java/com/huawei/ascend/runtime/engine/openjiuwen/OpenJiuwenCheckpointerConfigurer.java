package com.huawei.ascend.runtime.engine.openjiuwen;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import java.util.Objects;

/**
 * Startup-time configuration helper for OpenJiuwen's native checkpointer.
 *
 * <p>The runtime keeps checkpointer selection OpenJiuwen-local because other
 * agent frameworks have their own short-term state mechanisms. Call this from
 * application wiring before serving traffic; do not switch the global
 * CheckpointerFactory per request.
 */
public final class OpenJiuwenCheckpointerConfigurer {

    private OpenJiuwenCheckpointerConfigurer() {
    }

    /**
     * Set the OpenJiuwen default checkpointer and return the same instance for
     * Spring bean wiring.
     */
    public static Checkpointer setDefault(Checkpointer checkpointer) {
        Checkpointer stableCheckpointer = Objects.requireNonNull(checkpointer, "checkpointer");
        CheckpointerFactory.setDefaultCheckpointer(stableCheckpointer);
        return stableCheckpointer;
    }

    /**
     * Set a new OpenJiuwen in-memory checkpointer as the default path.
     */
    public static Checkpointer setInMemoryDefault() {
        return setDefault(new InMemoryCheckpointer());
    }
}
