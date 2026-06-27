package com.huawei.ascend.examples.workmate.filehistory;

public enum FileVersionOp {
    CREATED,
    MODIFIED,
    DELETED;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static FileVersionOp fromWire(String value) {
        if (value == null || value.isBlank()) {
            return MODIFIED;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
