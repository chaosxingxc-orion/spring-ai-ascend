package com.huawei.ascend.service.runtime.graphmemory;

import com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@ConditionalOnClass(GraphMemoryRepository.class)
@ConditionalOnProperty(prefix = "springai.ascend.graphmemory", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(GraphMemoryProperties.class)
public class GraphMemoryAutoConfiguration {
}
