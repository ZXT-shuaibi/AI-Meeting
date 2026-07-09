package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

import java.util.List;

@Builder
public record AgenticInterviewStagePlanResult(
        List<InterviewStagePlan> stages
) {
    public AgenticInterviewStagePlanResult {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
}
