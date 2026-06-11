/**
 * Spring Boot edge of the agent-service serviceization facade. Auto-configures
 * the registration/discovery/route-grant HTTP controllers over the Spring-free
 * SPI in {@code com.huawei.ascend.service.spi} and the JWT tenant cross-check
 * filter at the service ingress. Every bean backs off behind
 * {@code @ConditionalOnMissingBean} so deployments can substitute their own
 * registry, directory, or grant implementations.
 */
package com.huawei.ascend.service.starter;
