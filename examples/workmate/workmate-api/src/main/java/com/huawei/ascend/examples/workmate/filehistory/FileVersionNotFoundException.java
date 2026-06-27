package com.huawei.ascend.examples.workmate.filehistory;

public class FileVersionNotFoundException extends RuntimeException {

    public FileVersionNotFoundException(String versionId, String path) {
        super("File version not found: " + versionId + " for path " + path);
    }
}
