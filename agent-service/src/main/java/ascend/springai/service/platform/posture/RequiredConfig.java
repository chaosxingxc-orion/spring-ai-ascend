package ascend.springai.service.platform.posture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation: the annotated {@code @ConfigurationProperties} field MUST
 * be populated in {@code research}/{@code prod} posture. {@link PostureBootGuard}
 * inspects the matrix via direct property accessors at L1; the annotation is
 * primarily documentation for future waves and for the gate scanner (Phase I
 * may consume it to auto-generate the required-config table).
 *
 * <p>Enforcer rows: docs/governance/enforcers.yaml#E21.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface RequiredConfig {

    /**
     * Postures where this config field must be set. Defaults to research + prod.
     */
    String[] in() default {"research", "prod"};
}
