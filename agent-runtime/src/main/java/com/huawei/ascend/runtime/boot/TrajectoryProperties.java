package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for northbound trajectory observability. {@code enabled} is the only
 * switch; a request may opt out via the {@code trajectory.level=off} A2A metadata key.
 */
@ConfigurationProperties(prefix = "app.trajectory")
public class TrajectoryProperties {

    private boolean enabled = true;
    private final Mask mask = new Mask();
    private final Otel otel = new Otel();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Mask getMask() { return mask; }

    public Otel getOtel() { return otel; }

    public static class Mask {
        private String keyPattern = TrajectoryMasking.DEFAULT_KEY_PATTERN;
        private int truncateChars = 256;

        public String getKeyPattern() { return keyPattern; }

        public void setKeyPattern(String keyPattern) { this.keyPattern = keyPattern; }

        public int getTruncateChars() { return truncateChars; }

        public void setTruncateChars(int truncateChars) { this.truncateChars = truncateChars; }
    }

    /** Optional OpenTelemetry span export of the trajectory. Off by default. */
    public static class Otel {
        private boolean enabled = false;
        private String endpoint = "http://localhost:4317";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getEndpoint() { return endpoint; }

        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    }
}
