package com.huawei.ascend.service.engine.adapter.openjiuwen;

import com.huawei.ascend.service.engine.model.InterruptType;
import java.util.Map;
import com.huawei.ascend.service.engine.spi.AgentExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps openJiuwen's {@code Runner.runAgent} result map to a framework-neutral
 * agent result, per the execution contract in design §10.4:
 * {@code result_type ∈ {answer, error, interrupt}} →
 * completed / failed / interrupted.
 */
public class OpenJiuwenResultMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenJiuwenResultMapper.class);

    static final String ERROR_CODE = "OPENJIUWEN_ERROR";

    public AgentExecutionResult map(Map<String, Object> result) {
        String type = result == null ? null : asString(result.get("result_type"));
        String output = result == null ? "" : asString(result.get("output"));
        LOGGER.info("openjiuwen result map resultType={} outputLength={} keys={}",
                type,
                output.length(),
                result == null ? "null" : result.keySet());
        if ("answer".equals(type)) {
            return AgentExecutionResult.completed(output);
        }
        if ("interrupt".equals(type)) {
            return AgentExecutionResult.interrupted(InterruptType.HUMAN_INPUT, output);
        }
        return AgentExecutionResult.failed(ERROR_CODE, output);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
