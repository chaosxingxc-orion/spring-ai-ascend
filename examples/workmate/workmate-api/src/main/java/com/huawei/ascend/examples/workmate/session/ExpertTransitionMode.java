package com.huawei.ascend.examples.workmate.session;

/** How expert binding changes relate to workspace and conversation continuity (W53). */
public enum ExpertTransitionMode {
    /** Same session: inherit workspace + chat semantics via soft handoff. */
    SUMMON_IN_SESSION,
    /** New session with same workspace (W47 change-expert). */
    CHANGE_EXPERT,
    /** Brand-new session and workspace. */
    SUMMON_NEW_TASK
}
