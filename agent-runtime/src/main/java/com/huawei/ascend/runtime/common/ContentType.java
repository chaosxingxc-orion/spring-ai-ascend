package com.huawei.ascend.runtime.common;

/**
 * Canonical content-type discriminators for {@link Content}.
 *
 * <p>Mirrors the agentscope-runtime {@code ContentType} constants. Kept as
 * string constants (not an enum) so adapters can carry framework-specific
 * content types through without losing information.
 */
public final class ContentType {

    public static final String TEXT = "text";
    public static final String DATA = "data";
    public static final String IMAGE = "image";
    public static final String AUDIO = "audio";
    public static final String FILE = "file";
    public static final String TOOL_RESULT = "tool_result";
    public static final String ARTIFACT_REF = "artifact_ref";

    private ContentType() {
    }
}
