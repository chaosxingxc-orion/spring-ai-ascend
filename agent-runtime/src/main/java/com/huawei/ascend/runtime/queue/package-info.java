/**
 * A business-agnostic queue capability. This layer knows nothing about
 * {@code AgentRequest}, {@code TaskRecord} or any other business type — it only
 * provides queue primitives ({@code RuntimeQueue}, {@code QueueManager}, and the
 * publisher/subscriber/factory types).
 *
 * <p>access, control and engine each use it to create their OWN internal queues
 * and never expose a queue type across a layer boundary; cross-layer interaction
 * goes through the layers' APIs, not by passing queue instances. Keeping queue
 * free of business meaning is what lets all three layers share it without
 * coupling to one another.
 *
 * <p>The package is intentionally flat — no {@code api}/{@code memory}/
 * {@code manage} sub-packages. Dependency rule: JDK (and at most {@code common}
 * error types) only; no Spring Boot, no business module.
 */
package com.huawei.ascend.runtime.queue;
