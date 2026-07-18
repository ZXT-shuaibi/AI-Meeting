package com.hewei.hzyjy.xunzhi.career.resume.application;

import com.hewei.hzyjy.xunzhi.career.agent.cv.CvOptimizationResult;

import java.time.Instant;

public record ResumeOptimizationHistoryResult(
        String taskId,
        Long resumeId,
        String originalFilename,
        String jobDescription,
        CvOptimizationResult result,
        Instant optimizedAt
) {
}
