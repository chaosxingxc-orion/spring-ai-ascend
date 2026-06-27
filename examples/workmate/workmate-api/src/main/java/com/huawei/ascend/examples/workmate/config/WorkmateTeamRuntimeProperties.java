package com.huawei.ascend.examples.workmate.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.team-runtime")
public class WorkmateTeamRuntimeProperties {

    /** Default runtime when expert.yaml has no runtime field: workmate-orchestrator | openjiuwen-team */
    private String defaultRuntime = "workmate-orchestrator";

    /** Per-expert allowlist for openjiuwen-team (e.g. enterprise-legal-team). */
    private List<String> allowlist = new ArrayList<>();

    public String getDefaultRuntime() {
        return defaultRuntime;
    }

    public void setDefaultRuntime(String defaultRuntime) {
        this.defaultRuntime = defaultRuntime;
    }

    public List<String> getAllowlist() {
        return allowlist;
    }

    public void setAllowlist(List<String> allowlist) {
        this.allowlist = allowlist != null ? allowlist : new ArrayList<>();
    }
}
