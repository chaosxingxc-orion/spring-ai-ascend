package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.service.AgentStateStore;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import java.util.Objects;

/**
 * Factory helpers for wiring OpenJiuwen native checkpoints into the runtime
 * {@link AgentStateStore}.
 */
public final class OpenJiuwenAgentStateCheckpointers {

    private OpenJiuwenAgentStateCheckpointers() {
    }

    public static Checkpointer create(AgentStateStore stateStore) {
        return new PersistenceCheckpointer(new AgentStateStoreBackedOpenJiuwenKvStore(
                Objects.requireNonNull(stateStore, "stateStore")));
    }

    public static Checkpointer installDefault(AgentStateStore stateStore) {
        Checkpointer checkpointer = create(stateStore);
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        return checkpointer;
    }
}
