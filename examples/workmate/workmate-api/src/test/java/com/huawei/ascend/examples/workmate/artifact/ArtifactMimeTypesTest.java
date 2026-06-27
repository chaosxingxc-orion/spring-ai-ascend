package com.huawei.ascend.examples.workmate.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArtifactMimeTypesTest {

    @Test
    void guessesCommonExtensions() {
        assertThat(ArtifactMimeTypes.guess("hello.md")).isEqualTo("text/markdown");
        assertThat(ArtifactMimeTypes.guess("index.html")).isEqualTo("text/html");
        assertThat(ArtifactMimeTypes.guess("data.bin")).isEqualTo("application/octet-stream");
    }
}
