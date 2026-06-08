package com.huawei.ascend.agentsdk.spec.yaml;

import com.huawei.ascend.agentsdk.support.ValidationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentYamlEnvironmentResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    public String resolve(String yaml) {
        if (yaml == null || yaml.isEmpty()) {
            return yaml;
        }
        Matcher matcher = PLACEHOLDER.matcher(yaml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = System.getenv(key);
            if (value == null) {
                throw new ValidationException("Environment variable is not set: " + key);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

