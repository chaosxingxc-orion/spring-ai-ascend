package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for northbound trajectory observability. {@code enabled} is the only
 * switch; a request may opt out via the {@code trajectory.level=off} A2A metadata key.
 */
@ConfigurationProperties(prefix = "app.trajectory")
public class TrajectoryProperties {

    private boolean enabled = true;
    private double sampleRate = 1.0;
    private final Mask mask = new Mask();
    private final Otel otel = new Otel();
    private final Log log = new Log();
    private final Redact redact = new Redact();
    private final Pricing pricing = new Pricing();
    private final PayloadRef payloadRef = new PayloadRef();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public double getSampleRate() { return sampleRate; }

    public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }

    public Mask getMask() { return mask; }

    public Otel getOtel() { return otel; }

    public Log getLog() { return log; }

    public Redact getRedact() { return redact; }

    public Pricing getPricing() { return pricing; }

    public PayloadRef getPayloadRef() { return payloadRef; }

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

    /** Optional NDJSON structured-log export of the trajectory. Off by default. */
    public static class Log {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** Value-level content redaction (credit-card / SSN / GPS) on top of key masking. Off by default. */
    public static class Redact {
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** Per-model token pricing for FinOps cost enrichment. Off by default. */
    public static class Pricing {
        private boolean enabled = false;
        private Map<String, Model> models = new LinkedHashMap<>();

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Map<String, Model> getModels() { return models; }

        public void setModels(Map<String, Model> models) { this.models = models; }

        /** Per-token price (integer micro-currency) and provider for one model id. */
        public static class Model {
            private String provider;
            private long inputMicrosPerToken;
            private long outputMicrosPerToken;

            public String getProvider() { return provider; }

            public void setProvider(String provider) { this.provider = provider; }

            public long getInputMicrosPerToken() { return inputMicrosPerToken; }

            public void setInputMicrosPerToken(long inputMicrosPerToken) {
                this.inputMicrosPerToken = inputMicrosPerToken;
            }

            public long getOutputMicrosPerToken() { return outputMicrosPerToken; }

            public void setOutputMicrosPerToken(long outputMicrosPerToken) {
                this.outputMicrosPerToken = outputMicrosPerToken;
            }
        }
    }

    /**
     * Write-only externalization of oversized string payloads. When enabled, payload strings
     * longer than {@code app.trajectory.mask.truncate-chars} are written to {@code directory}
     * and replaced by a {@code payload_ref://sha256} reference in the event. Off by default.
     */
    public static class PayloadRef {
        private boolean enabled = false;
        private String directory = System.getProperty("java.io.tmpdir") + "/trajectory-payloads";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getDirectory() { return directory; }

        public void setDirectory(String directory) { this.directory = directory; }
    }
}
