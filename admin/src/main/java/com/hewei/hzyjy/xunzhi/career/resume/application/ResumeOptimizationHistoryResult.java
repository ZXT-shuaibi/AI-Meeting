package com.hewei.hzyjy.xunzhi.career.resume.application;

import java.time.Instant;
import java.util.Map;

public record ResumeOptimizationHistoryResult(
        String taskId,
        Long resumeId,
        String originalFilename,
        String jobDescription,
        Map<String, Object> result,
        Instant optimizedAt
) {
}
