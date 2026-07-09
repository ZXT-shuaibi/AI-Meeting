package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

import java.util.List;

@Builder
public record InterviewStagePlan(
        String stageName,
        String goal,
        List<String> questionSeeds
) {
    public InterviewStagePlan {
        stageName = stageName == null ? "" : stageName;
        goal = goal == null ? "" : goal;
        questionSeeds = questionSeeds == null ? List.of() : List.copyOf(questionSeeds);
    }
}
