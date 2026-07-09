package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;

public record ResumeParseTaskResult(
        String taskId,
        Long userId,
        String status,
        String statusMessage,
        Integer progress,
        Long estimatedRemainingSeconds,
        Long resumeId,
        String originalFilename,
        Long fileSize,
        String contentType,
        String storageProvider,
        String storageKey,
        String retryOfTaskId,
        String errorMessage,
        Instant startTime,
        Instant completeTime
) {
    public static ResumeParseTaskResult notFound(String taskId) {
        return new ResumeParseTaskResult(
                taskId,
                null,
                "NOT_FOUND",
                "任务不存在",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Parse task not found",
                null,
                null
        );
    }
}
