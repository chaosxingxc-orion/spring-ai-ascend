package com.huawei.ascend.runtime.common;

/**
 * Kind of a {@link RunEvent} on the neutral output stream.
 *
 * <p>ACCEPTED marks intake, CHUNK carries an incremental output fragment, COMPLETED is the
 * terminal success event, FAILED is the terminal failure event.
 */
public enum RunEventType {
    ACCEPTED,
    CHUNK,
    COMPLETED,
    FAILED
}
