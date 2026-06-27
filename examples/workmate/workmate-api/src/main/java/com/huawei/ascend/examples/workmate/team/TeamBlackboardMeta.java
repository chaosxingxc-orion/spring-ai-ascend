package com.huawei.ascend.examples.workmate.team;

import java.time.Instant;

/** Version metadata for shared-state blackboard writes (W27). */
record TeamBlackboardMeta(long version, Instant updatedAt) {}
