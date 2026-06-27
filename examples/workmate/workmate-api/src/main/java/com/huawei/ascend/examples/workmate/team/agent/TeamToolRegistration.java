package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory.McpToolSet;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory.AskUserQuestionToolSet;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory.WorkspaceToolSet;

public record TeamToolRegistration(
        WorkspaceToolSet workspace, McpToolSet mcp, AskUserQuestionToolSet ask) {}
