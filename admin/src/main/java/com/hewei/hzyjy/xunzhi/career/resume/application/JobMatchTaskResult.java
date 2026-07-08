package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;

public record JobMatchTaskResult(
        String taskId,
        Long userId,
        String status,
        List<String> matchedTemplates,
        String errorMessage
) {
    public static JobMatchTaskResult started(String taskId, Long userId) {
        return new JobMatchTaskResult(taskId, userId, "STARTED", List.of(), null);
    }

    public static JobMatchTaskResult completed(String taskId, Long userId, List<String> matchedTemplates) {
        return new JobMatchTaskResult(taskId, userId, "COMPLETED", matchedTemplates == null ? List.of() : matchedTemplates, null);
    }

    public static JobMatchTaskResult failed(String taskId, Long userId, String errorMessage) {
        return new JobMatchTaskResult(taskId, userId, "FAILED", List.of(), errorMessage);
    }

    public static JobMatchTaskResult notFound(String taskId) {
        return new JobMatchTaskResult(taskId, null, "NOT_FOUND", List.of(), "Task not found");
    }
}
