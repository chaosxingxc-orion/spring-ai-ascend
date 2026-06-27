package com.huawei.ascend.examples.workmate.session.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

public record CreateSessionResponse(
        @JsonUnwrapped SessionResponse session, List<AutoArchivedSession> autoArchived) {}
