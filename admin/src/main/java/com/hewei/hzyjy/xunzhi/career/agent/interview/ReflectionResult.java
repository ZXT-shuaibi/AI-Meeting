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
}
