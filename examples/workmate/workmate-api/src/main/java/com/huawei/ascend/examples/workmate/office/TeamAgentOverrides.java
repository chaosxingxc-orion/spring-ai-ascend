package com.huawei.ascend.examples.workmate.office;

public record TeamAgentOverrides(String teamMode, String spawnMode, String teammateMode) {

    public String resolvedTeamMode() {
        return teamMode != null && !teamMode.isBlank() ? teamMode : "hybrid";
    }

    public String resolvedSpawnMode() {
        return spawnMode != null && !spawnMode.isBlank() ? spawnMode : "inprocess";
    }

    public String resolvedTeammateMode() {
        return teammateMode != null && !teammateMode.isBlank() ? teammateMode : "build_mode";
    }
}
