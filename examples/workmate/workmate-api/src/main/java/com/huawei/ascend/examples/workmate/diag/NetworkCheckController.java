package com.huawei.ascend.examples.workmate.diag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diag/network")
public class NetworkCheckController {

    private final NetworkCheckService networkCheckService;

    public NetworkCheckController(NetworkCheckService networkCheckService) {
        this.networkCheckService = networkCheckService;
    }

    @GetMapping
    public NetworkCheckReport check() {
        return networkCheckService.runChecks();
    }
}
