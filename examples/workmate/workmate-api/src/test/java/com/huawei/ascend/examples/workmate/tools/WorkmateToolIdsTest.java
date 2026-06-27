package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkmateToolIdsTest {

    private static final String SESSION = "7c8a59e7-5e67-4aba-8519-3fceb772b5d9";

    @Test
    void buildsScopedIds() {
        assertThat(WorkmateToolIds.read(SESSION)).isEqualTo("workmate_read__" + SESSION);
        assertThat(WorkmateToolIds.write(SESSION)).isEqualTo("workmate_write__" + SESSION);
        assertThat(WorkmateToolIds.bash(SESSION)).isEqualTo("workmate_bash__" + SESSION);
    }

    @Test
    void recognizesScopedKinds() {
        assertThat(WorkmateToolIds.isRead(WorkmateToolIds.read(SESSION))).isTrue();
        assertThat(WorkmateToolIds.isWrite(WorkmateToolIds.write(SESSION))).isTrue();
        assertThat(WorkmateToolIds.isBash(WorkmateToolIds.bash(SESSION))).isTrue();
        assertThat(WorkmateToolIds.isWorkspaceTool("other_tool")).isFalse();
    }
}
