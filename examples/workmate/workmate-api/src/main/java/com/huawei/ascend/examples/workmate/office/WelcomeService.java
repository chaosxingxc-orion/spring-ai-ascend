package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.WelcomeResponse;
import org.springframework.stereotype.Service;

@Service
public class WelcomeService {

    private final WelcomeRegistry registry;

    public WelcomeService(WelcomeRegistry registry) {
        this.registry = registry;
    }

    public WelcomeResponse getWelcome() {
        return WelcomeResponse.from(registry.document());
    }
}
