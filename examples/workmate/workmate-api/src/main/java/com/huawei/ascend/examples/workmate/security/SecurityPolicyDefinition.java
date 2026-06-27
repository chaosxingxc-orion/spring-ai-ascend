package com.huawei.ascend.examples.workmate.security;

import java.util.List;

public record SecurityPolicyDefinition(
        List<String> domainAllowList,
        List<String> domainDenyList,
        List<String> bashAskPatterns,
        List<String> bashBlockPatterns,
        List<String> fileBlockPatterns) {

    public static SecurityPolicyDefinition defaults() {
        return new SecurityPolicyDefinition(
                List.of(),
                List.of("*.malware.example"),
                List.of("rm\\s", "sudo\\s", "chmod\\s+777"),
                List.of("mkfs", "dd\\s+if="),
                List.of("**/.env", "**/credentials.json"));
    }

    public SecurityPolicyDefinition {
        domainAllowList = domainAllowList == null ? List.of() : List.copyOf(domainAllowList);
        domainDenyList = domainDenyList == null ? List.of() : List.copyOf(domainDenyList);
        bashAskPatterns = bashAskPatterns == null ? List.of() : List.copyOf(bashAskPatterns);
        bashBlockPatterns = bashBlockPatterns == null ? List.of() : List.copyOf(bashBlockPatterns);
        fileBlockPatterns = fileBlockPatterns == null ? List.of() : List.copyOf(fileBlockPatterns);
    }
}
