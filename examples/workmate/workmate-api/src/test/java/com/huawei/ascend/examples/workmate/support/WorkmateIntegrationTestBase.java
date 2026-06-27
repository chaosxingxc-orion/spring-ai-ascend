package com.huawei.ascend.examples.workmate.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for full-stack MockMvc integration tests. Subclasses supply a {@code @DynamicPropertySource}
 * that calls {@link WorkmateTestProperties#registerBaseline}.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class WorkmateIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
}
