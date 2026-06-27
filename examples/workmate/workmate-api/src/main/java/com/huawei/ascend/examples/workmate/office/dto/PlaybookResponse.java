package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.PlaybookDefinition;

public record PlaybookResponse(
        String id,
        String title,
        String description,
        String accent,
        String expertId,
        String initPrompt,
        java.util.List<String> placements) {

    public static PlaybookResponse from(PlaybookDefinition playbook) {
        return new PlaybookResponse(
                playbook.id(),
                playbook.title(),
                playbook.description(),
                playbook.accent(),
                playbook.expertId(),
                playbook.initPrompt(),
                playbook.placements());
    }
}
