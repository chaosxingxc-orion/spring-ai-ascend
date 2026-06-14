/**
 * Runtime machinery for the C3 forwarding substrate — currently the pure
 * state-transition engine ({@code ForwardingStateMachine}). Ports are wired to
 * in-memory test doubles in Stage 7; the real persistent / delivery binding is
 * Stage 8.
 *
 * <p>This package MUST NOT depend on a concrete broker / MQ client or a JDBC
 * driver in Stage 7 (Stage 7 plan §3 slice 4 boundary).
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4}.
 */
package com.huawei.ascend.bus.forwarding.runtime;
