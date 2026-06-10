package com.huawei.ascend.runtime.engine.openjiuwen;

import com.openjiuwen.core.singleagent.rail.AgentRail;

/**
 * Runtime-owned openJiuwen rail reserved for framework-local decorations.
 *
 * <p>The first implementation is intentionally passive. It gives the runtime a
 * stable rail installation point while keeping business agents free from
 * hand-written before/after hooks.
 */
public class OpenJiuwenRuntimeRail extends AgentRail {
}
