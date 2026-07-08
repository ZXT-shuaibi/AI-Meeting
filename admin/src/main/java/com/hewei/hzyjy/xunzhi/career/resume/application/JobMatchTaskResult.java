package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;

public record JobMatchTaskResult(
        String taskId,
        String status,
        List<String> matchedTemplates,
        String errorMessage
) {
    public static JobMatchTaskResult notFound(String taskId) {
        return new JobMatchTaskResult(taskId, "NOT_FOUND", List.of(), "Task not found");
    }
}
