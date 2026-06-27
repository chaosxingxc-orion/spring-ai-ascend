package com.huawei.ascend.examples.workmate.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/security/policy")
public class SecurityPolicyController {

    private final SecurityPolicyService policyService;

    public SecurityPolicyController(SecurityPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public SecurityPolicyDefinition getPolicy() {
        return policyService.getPolicy();
    }

    @PutMapping
    public SecurityPolicyDefinition updatePolicy(@RequestBody SecurityPolicyDefinition policy) {
        return policyService.updatePolicy(policy);
    }

    @PostMapping("/reset")
    public SecurityPolicyDefinition resetPolicy() {
        return policyService.resetPolicy();
    }
}
