package ascend.springai.service.runtime.resilience;

/**
 * Immutable triple of Resilience4j policy names for one operation.
 * Each name corresponds to a key in the application's resilience4j configuration.
 */
public record ResiliencePolicy(String cbName, String retryName, String tlName) {

    public ResiliencePolicy {
        if (cbName == null || retryName == null || tlName == null) {
            throw new IllegalArgumentException("ResiliencePolicy names must be non-null");
        }
    }
}
