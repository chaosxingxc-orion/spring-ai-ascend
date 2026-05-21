package com.huawei.ascend.service.runtime.graphmemory;

import com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GraphMemoryAutoConfiguration.class));

    @Test
    void contextLoads_noGraphMemoryRepositoryBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(GraphMemoryRepository.class);
        });
    }
}
