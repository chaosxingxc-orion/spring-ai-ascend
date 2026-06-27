package com.huawei.ascend.examples.workmate.office;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class OfficeImportLoader {

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;

    OfficeImportLoader(ExpertRegistry expertRegistry, SkillRegistry skillRegistry) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
    }

    @PostConstruct
    void loadImportedAssets() {
        expertRegistry.reloadAll();
        skillRegistry.reloadAll();
    }
}
