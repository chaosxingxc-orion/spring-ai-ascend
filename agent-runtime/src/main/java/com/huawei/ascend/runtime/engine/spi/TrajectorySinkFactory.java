package com.huawei.ascend.runtime.engine.spi;

/**
 * Builds a fresh per-invocation {@link TrajectorySink}. Optional infra sinks (e.g. an
 * OpenTelemetry exporter that holds per-invocation span state) are contributed as beans
 * of this type; the runtime invokes {@link #create()} once per invocation and adds the
 * result to the fan-out. Keeping the runtime behind this neutral factory means optional
 * sink dependencies (OTel) never have to be on the classpath when the feature is off.
 */
@FunctionalInterface
public interface TrajectorySinkFactory {

    TrajectorySink create();
}
