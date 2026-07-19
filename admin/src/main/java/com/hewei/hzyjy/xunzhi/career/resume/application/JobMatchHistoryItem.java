package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;
import java.util.List;

/** User-scoped summary for displaying recent JD matching runs. */
public record JobMatchHistoryItem(
        String taskId,
        String status,
        String jobDescription,
        int selectedResumeCount,
        List<JobMatchCandidate> matchedResumes,
        String errorMessage,
        Instant createdAt
) {
    public JobMatchHistoryItem {
        matchedResumes = matchedResumes == null ? List.of() : List.copyOf(matchedResumes);
    }
}
