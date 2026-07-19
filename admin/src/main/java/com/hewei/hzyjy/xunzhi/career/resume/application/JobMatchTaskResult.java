package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.util.List;

public record JobMatchTaskResult(
        String taskId,
        Long userId,
        String status,
        List<String> matchedTemplates,
        List<Long> selectedResumeIds,
        List<JobMatchCandidate> matchedResumes,
        String errorMessage
) {
    public JobMatchTaskResult {
        matchedTemplates = matchedTemplates == null ? List.of() : List.copyOf(matchedTemplates);
        selectedResumeIds = selectedResumeIds == null ? List.of() : List.copyOf(selectedResumeIds);
        matchedResumes = matchedResumes == null ? List.of() : List.copyOf(matchedResumes);
    }

    public static JobMatchTaskResult started(String taskId, Long userId) {
        return started(taskId, userId, List.of());
    }

    public static JobMatchTaskResult started(String taskId, Long userId, List<Long> selectedResumeIds) {
        return new JobMatchTaskResult(taskId, userId, "STARTED", List.of(), selectedResumeIds, List.of(), null);
    }

    public static JobMatchTaskResult completed(String taskId, Long userId, List<String> matchedTemplates) {
        return completed(taskId, userId, List.of(), List.of(), matchedTemplates);
    }

    public static JobMatchTaskResult completed(
            String taskId,
            Long userId,
            List<Long> selectedResumeIds,
            List<JobMatchCandidate> matchedResumes,
            List<String> matchedTemplates) {
        return new JobMatchTaskResult(taskId, userId, "COMPLETED", matchedTemplates, selectedResumeIds, matchedResumes, null);
    }

    public static JobMatchTaskResult failed(String taskId, Long userId, String errorMessage) {
        return failed(taskId, userId, List.of(), errorMessage);
    }

    public static JobMatchTaskResult failed(String taskId, Long userId, List<Long> selectedResumeIds, String errorMessage) {
        return new JobMatchTaskResult(taskId, userId, "FAILED", List.of(), selectedResumeIds, List.of(), errorMessage);
    }

    public static JobMatchTaskResult notFound(String taskId) {
        return new JobMatchTaskResult(taskId, null, "NOT_FOUND", List.of(), List.of(), List.of(), "Task not found");
    }
}
