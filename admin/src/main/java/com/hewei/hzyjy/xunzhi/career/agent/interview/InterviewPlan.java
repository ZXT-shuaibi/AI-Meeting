package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

import java.util.List;

@Builder
public record InterviewPlan(
        String sessionId,
        JdAlignmentResult alignment,
        List<InterviewStagePlan> stages,
        String firstQuestion
) {
}
