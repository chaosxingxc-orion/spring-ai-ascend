package com.huawei.ascend.examples.workmate.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class SecurityPolicyStore {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityPolicyStore.class);
    private static final String FILE_NAME = "security-policy.json";

    private final JsonFileStore<SecurityPolicyDefinition> backing;

    SecurityPolicyStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                SecurityPolicyDefinition.class,
                SecurityPolicyDefinition::defaults,
                LOG);
    }

    SecurityPolicyDefinition get() {
        return backing.read(policy -> policy);
    }

    void save(SecurityPolicyDefinition next) {
        backing.replace(next);
    }

    void reset() {
        backing.replace(SecurityPolicyDefinition.defaults());
    }
}
