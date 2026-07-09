package com.hewei.hzyjy.xunzhi.career.agent.interview;

import lombok.Builder;

import java.util.List;

@Builder
public record ReflectionResult(
        int score,
        ReflectionDecision decision,
        String feedback,
        List<String> probeSuggestions
) {
    public ReflectionResult {
        feedback = feedback == null ? "" : feedback;
        probeSuggestions = probeSuggestions == null ? List.of() : List.copyOf(probeSuggestions);
    }
}
