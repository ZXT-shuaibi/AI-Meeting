package com.hewei.hzyjy.xunzhi.career.resume.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalResumeObjectStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsObjectBySafeKey() throws Exception {
        LocalResumeObjectStorage storage = new LocalResumeObjectStorage("local", tempDir.toString(), "");

        ResumeObjectStorageResult result = storage.put(
                "career/resume/parse-task/task-1/resume.txt",
                "Java Redis".getBytes(StandardCharsets.UTF_8),
                "text/plain",
                "resume.txt"
        );

        assertThat(result.provider()).isEqualTo("local");
        assertThat(result.key()).isEqualTo("career/resume/parse-task/task-1/resume.txt");
        assertThat(result.path()).contains("resume.txt");
        assertThat(new String(storage.get(result.key()).readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("Java Redis");
    }

    @Test
    void rejectsPathTraversalKeys() {
        LocalResumeObjectStorage storage = new LocalResumeObjectStorage("local", tempDir.toString(), "");

        assertThatThrownBy(() -> storage.put("../escape.txt", new byte[]{1}, "text/plain", "escape.txt"))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
