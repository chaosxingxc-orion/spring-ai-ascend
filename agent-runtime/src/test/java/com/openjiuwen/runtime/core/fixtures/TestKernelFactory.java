package com.openjiuwen.runtime.core.fixtures;

import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import com.openjiuwen.runtime.core.engine.DefaultSafetyBoundary;
import com.openjiuwen.runtime.core.engine.SafetyBoundary;
import com.openjiuwen.core.kernel.model.ToolName;

import java.util.Map;

/**
 * Factory for creating test AgentKernel instances with common configurations.
 */
public class TestKernelFactory {

    /** Create a kernel with mock LLM, no tools, and default safety boundary */
    public static DefaultAgentKernel createKernel(String llmResponse) {
        return createKernel(new MockChatModel(llmResponse), Map.of(), new DefaultSafetyBoundary());
    }

    /** Create a kernel with mock LLM, tools, and default safety boundary */
    public static DefaultAgentKernel createKernel(String llmResponse,
                                                   MockToolProvider toolProvider) {
        return createKernel(new MockChatModel(llmResponse), toolProvider.build(), new DefaultSafetyBoundary());
    }

    /** Create a kernel with all components specified */
    public static DefaultAgentKernel createKernel(MockChatModel llmProvider,
                                                   Map<ToolName, DefaultAgentKernel.ToolExecutor> tools,
                                                   SafetyBoundary safetyBoundary) {
        return new DefaultAgentKernel(llmProvider, tools, new MockCheckpointStore(), safetyBoundary);
    }

    /** Create a kernel with custom checkpoint store for recovery testing */
    public static DefaultAgentKernel createKernelWithStore(MockChatModel llmProvider,
                                                            MockToolProvider toolProvider,
                                                            MockCheckpointStore checkpointStore,
                                                            SafetyBoundary safetyBoundary) {
        return new DefaultAgentKernel(llmProvider, toolProvider.build(), checkpointStore, safetyBoundary);
    }
}
